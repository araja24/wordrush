package dev.wordrush.chat;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * At most {@link #MAX_PER_WINDOW} chat messages per user per {@link #WINDOW}, counted in Redis so
 * the limit holds across instances. The window opens with the user's first message: the counter
 * is created with its TTL ({@code SET NX EX}) and then incremented, so a crash between the two
 * calls can never leave a counter that never expires.
 */
@Component
class ChatRateLimiter {

    static final int MAX_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;

    ChatRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean tryAcquire(UUID userId) {
        String key = "wr:rl:" + userId + ":chat";
        redis.opsForValue().setIfAbsent(key, "0", WINDOW);
        Long count = redis.opsForValue().increment(key);
        return count != null && count <= MAX_PER_WINDOW;
    }
}
