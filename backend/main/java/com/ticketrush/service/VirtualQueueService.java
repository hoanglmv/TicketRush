package com.ticketrush.service;

import com.ticketrush.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualQueueService {

    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:";
    private static final String ACCESS_KEY_PREFIX = "access:";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);

    /**
     * Thêm người dùng vào hàng đợi ảo (Virtual Queue) cho một sự kiện.
     * Sử dụng Redis ZSET (Sorted Set) với thời điểm tham gia (timestamp) làm điểm số (score) để đảm bảo tính công bằng (FIFO - Ai vào trước đứng trước).
     */
    public QueueStatusResponse joinQueue(Long userId, Long eventId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId; // VD: queue:1
        String userIdStr = userId.toString();

        // Bước 1: Kiểm tra xem người dùng đã được cấp quyền mua vé (có token) hay chưa.
        // Tránh trường hợp họ có quyền rồi mà vẫn đi xếp hàng lại.
        String existingToken = getAccessToken(userId, eventId);
        if (existingToken != null) {
            return QueueStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .hasAccess(true)
                    .accessToken(existingToken)
                    .message("You already have access to book seats")
                    .build();
        }

        // Bước 2: Đưa người dùng vào hàng đợi (ZADD)
        // redisTemplate.opsForZSet().score() sẽ trả về null nếu user chưa có trong hàng đợi.
        Double existingScore = redisTemplate.opsForZSet().score(queueKey, userIdStr);
        if (existingScore == null) {
            // Thêm user vào ZSET với số điểm là thời gian hiện tại (Millisecond).
            // Do là ZSET, Redis sẽ tự động sắp xếp mảng này tăng dần theo điểm số (thời gian).
            redisTemplate.opsForZSet().add(queueKey, userIdStr, System.currentTimeMillis());
        }

        // Bước 3: Tính toán vị trí (Rank) hiện tại của người dùng.
        // hàm rank() trả về thứ hạng bắt đầu từ 0 (như index của mảng).
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userIdStr);
        Long totalInQueue = redisTemplate.opsForZSet().size(queueKey);

        return QueueStatusResponse.builder()
                .eventId(eventId)
                .userId(userId)
                // rank + 1 để biến thứ hạng (0,1,2...) thành vị trí thực tế hiển thị cho người dùng (1,2,3...)
                .position(rank != null ? rank + 1 : null)
                .totalInQueue(totalInQueue)
                .hasAccess(false) // Mới xếp hàng nên chưa có quyền mua vé
                .message(String.format("You are at position %d in the queue. Please wait...", rank != null ? rank + 1 : 0))
                .build();
    }

    /**
     * Kiểm tra trạng thái hiện tại của người dùng.
     * Hàm này được Frontend gọi liên tục (polling) hoặc gọi qua WebSocket để cập nhật giao diện.
     */
    public QueueStatusResponse checkStatus(Long userId, Long eventId) {
        // Kiểm tra xem hệ thống đã cấp token cho anh này chưa (Đã đến lượt chưa?)
        String token = getAccessToken(userId, eventId);
        if (token != null) {
            // Nếu có token rồi -> Xin chúc mừng, bạn được quyền vào chọn ghế!
            return QueueStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .hasAccess(true) // Cho phép vào chọn ghế
                    .accessToken(token) // Cấp luôn chiếc vé thông hành này
                    .message("You have access to book seats!")
                    .build();
        }

        // Nếu chưa đến lượt, đi tính toán lại vị trí hiện tại của họ trong hàng
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        Long totalInQueue = redisTemplate.opsForZSet().size(queueKey);

        if (rank == null) {
            // Xảy ra khi họ chưa từng gọi joinQueue hoặc đã bị thoát hàng do lỗi mạng quá lâu
            return QueueStatusResponse.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .hasAccess(false)
                    .message("You are not in the queue. Please join first.")
                    .build();
        }

        return QueueStatusResponse.builder()
                .eventId(eventId)
                .userId(userId)
                .position(rank + 1) // Báo lại vị trí mới (Sẽ giảm dần khi những người đứng trước mua vé xong)
                .totalInQueue(totalInQueue)
                .hasAccess(false)
                .message(String.format("You are at position %d of %d. Please wait...", rank + 1, totalInQueue))
                .build();
    }

    /**
     * Thuật toán "Xả hàng" - Được một hệ thống lên lịch (Scheduler) gọi liên tục mỗi vài giây.
     * Hàm này sẽ gắp ra N người đứng ở đầu hàng đợi (có thời gian chờ lâu nhất) và cấp quyền mua vé cho họ.
     * 
     * @param batchSize Số lượng người được thả vào trong 1 lượt (Ví dụ 10 người/lượt)
     */
    public int processQueue(Long eventId, int batchSize) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;

        // Lệnh popMin: Cắt N người có Score (Thời gian) thấp nhất ra khỏi tập hợp ZSET (Đồng nghĩa với việc rời khỏi hàng đợi).
        Set<ZSetOperations.TypedTuple<String>> batch = redisTemplate.opsForZSet()
                .popMin(queueKey, batchSize);

        if (batch == null || batch.isEmpty()) {
            return 0; // Hàng đợi đang trống
        }

        int processed = 0;
        for (ZSetOperations.TypedTuple<String> entry : batch) {
            String userIdStr = entry.getValue();
            if (userIdStr != null) {
                // Với từng người vừa được gắp ra, ta cấp cho họ 1 cái Token
                grantAccess(Long.parseLong(userIdStr), eventId);
                processed++;
            }
        }

        log.info("Processed {} users from queue for event {}", processed, eventId);
        return processed;
    }

    /**
     * Cấp quyền truy cập cho người dùng.
     * Bằng cách tạo ra một chuỗi Token ngẫu nhiên (UUID) và lưu vào Redis.
     * Đặc biệt: Đặt thời gian sống (TTL) cho Token này là 5 phút. Hết 5 phút Token sẽ tự bốc hơi.
     * Giúp hệ thống chống lại tình trạng ngâm vé (treo hệ thống).
     */
    private void grantAccess(Long userId, Long eventId) {
        String accessKey = ACCESS_KEY_PREFIX + eventId + ":" + userId;
        String token = UUID.randomUUID().toString(); // Sinh mã truy cập độc nhất
        // Lưu vào Redis dạng Key-Value (String) với thời gian hết hạn ACCESS_TOKEN_TTL
        redisTemplate.opsForValue().set(accessKey, token, ACCESS_TOKEN_TTL);
    }

    /**
     * Xác thực xem token truy cập có hợp lệ hay không.
     */
    public boolean validateAccess(Long userId, Long eventId, String token) {
        String accessKey = ACCESS_KEY_PREFIX + eventId + ":" + userId;
        String storedToken = redisTemplate.opsForValue().get(accessKey);
        return storedToken != null && storedToken.equals(token);
    }

    /**
     * Check if user has valid access (for internal use).
     */
    public boolean hasAccess(Long userId, Long eventId) {
        String accessKey = ACCESS_KEY_PREFIX + eventId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(accessKey));
    }

    private String getAccessToken(Long userId, Long eventId) {
        String accessKey = ACCESS_KEY_PREFIX + eventId + ":" + userId;
        return redisTemplate.opsForValue().get(accessKey);
    }

    /**
     * Bỏ xếp hàng (rời khỏi hàng đợi) khi người dùng thoát trang.
     */
    public void leaveQueue(Long userId, Long eventId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        redisTemplate.opsForZSet().remove(queueKey, userId.toString());
    }

    public Long getQueueSize(Long eventId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0;
    }
}
