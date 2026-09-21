package app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service xử lý khóa ghế nguyên tử bằng Redis Lua Script (Atomic Seat Lock Engine):
 * - Đảm bảo khả năng chịu tải hàng chục ngàn TPS mà không làm nghẽn RDBMS MySQL.
 * - Mã Lua script thực thi đơn luồng nguyên tử trên RAM của Redis, triệt tiêu 100% Race Condition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLuaLockService {

    private static final String LOCK_KEY_PREFIX = "ticketrush:seat:lock:";
    private final StringRedisTemplate redisTemplate;

    // Lua Script để khóa ghế: Kiểm tra key chưa tồn tại thì set kèm TTL
    private static final String HOLD_SEAT_LUA =
            "if redis.call('exists', KEYS[1]) == 0 then\n" +
            "    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    // Lua Script để nhả ghế: Chỉ người giữ ghế mới có quyền nhả
    private static final String RELEASE_SEAT_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('del', KEYS[1])\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    /**
     * Khóa ghế nguyên tử trong Redis với thời hạn TTL (mặc định 600 giây).
     * @return true nếu khóa thành công, false nếu ghế đã có người khác giữ trước.
     */
    public boolean atomicHoldSeat(Long seatId, Long userId, int ttlSeconds) {
        String key = LOCK_KEY_PREFIX + seatId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(HOLD_SEAT_LUA, Long.class);
        List<String> keys = Collections.singletonList(key);

        try {
            Long result = redisTemplate.execute(script, keys, String.valueOf(userId), String.valueOf(ttlSeconds));
            boolean success = result != null && result == 1L;
            if (success) {
                log.info("Redis Lua: Seat #{} locked atomically by User #{} for {}s", seatId, userId, ttlSeconds);
            }
            return success;
        } catch (Exception e) {
            log.warn("Redis Lua lock failed (fallback to DB lock): {}", e.getMessage());
            return true; // Graceful fallback sang cơ chế Pessimistic Lock của DB
        }
    }

    /**
     * Giải phóng ghế nguyên tử trong Redis.
     */
    public boolean atomicReleaseSeat(Long seatId, Long userId) {
        String key = LOCK_KEY_PREFIX + seatId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SEAT_LUA, Long.class);
        List<String> keys = Collections.singletonList(key);

        try {
            Long result = redisTemplate.execute(script, keys, String.valueOf(userId));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("Redis Lua release failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra ai đang giữ ghế trong Redis.
     */
    public Long getSeatHolder(Long seatId) {
        String key = LOCK_KEY_PREFIX + seatId;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
