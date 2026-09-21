package app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service quản lý Idempotency Key (Chống trùng lặp giao dịch & bảo vệ tài chính):
 * - Đảm bảo nguyên tắc Idempotent: 1 thao tác gửi 1 lần hay n lần đều cho kết quả duy nhất.
 * - Sử dụng Redis Atomic SetNX với trạng thái PROCESSING để làm Mutex Lock.
 * - Cache kết quả trả về trong 24h để trả về tức thì nếu có retry mạng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "ticketrush:idempotency:";
    private static final String STATUS_PROCESSING = "__PROCESSING__";
    private static final int DEFAULT_TTL_SECONDS = 86400; // 24 giờ

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Bắt đầu một giao dịch Idempotent.
     * @return true nếu đây là request mới và đã giành được quyền xử lý.
     * @throws IllegalStateException nếu request này đang trong tiến trình xử lý ở luồng khác.
     */
    public boolean startProcessing(String idempotencyKey, int lockTimeoutSeconds) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // Không có key thì bỏ qua kiểm tra
        }

        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                STATUS_PROCESSING,
                Duration.ofSeconds(lockTimeoutSeconds > 0 ? lockTimeoutSeconds : 60)
        );

        if (Boolean.TRUE.equals(acquired)) {
            return true;
        }

        // Nếu không giành được lock, kiểm tra xem có đang processing không
        String existingValue = redisTemplate.opsForValue().get(redisKey);
        if (STATUS_PROCESSING.equals(existingValue)) {
            throw new IllegalStateException("Giao dịch đang được xử lý bởi một yêu cầu khác. Vui lòng không gửi lại!");
        }

        return false; // Đã hoàn thành từ trước, có thể lấy cached response
    }

    /**
     * Lưu kết quả phản hồi thành công vào cache để phục vụ các lần retry tiếp theo.
     */
    public void saveResponse(String idempotencyKey, Object response) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || response == null) {
            return;
        }

        try {
            String redisKey = KEY_PREFIX + idempotencyKey.trim();
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
            log.info("Cached idempotent response for key: {}", idempotencyKey);
        } catch (Exception e) {
            log.warn("Failed to cache idempotent response: {}", e.getMessage());
        }
    }

    /**
     * Lấy kết quả đã được xử lý trước đó nếu cùng key gửi lại.
     */
    public <T> T getCachedResponse(String idempotencyKey, Class<T> targetClass) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        String json = redisTemplate.opsForValue().get(redisKey);

        if (json == null || STATUS_PROCESSING.equals(json)) {
            return null;
        }

        try {
            return objectMapper.readValue(json, targetClass);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Xóa key nếu xảy ra lỗi ngoại lệ trong quá trình xử lý, cho phép người dùng retry lại ngay.
     */
    public void release(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + idempotencyKey.trim());
        }
    }
}
