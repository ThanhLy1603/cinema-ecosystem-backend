package com.cinema.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        String redisKey = KEY_PREFIX + key;
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            valueOperations.setIfAbsent(redisKey, "0", window);
            Long attempts = valueOperations.increment(redisKey);
            return attempts == null || attempts <= maxAttempts;
        } catch (RuntimeException runtimeException) {
            log.warn("Redis không khả dụng, bỏ qua giới hạn tần suất cho khóa {}", key, runtimeException);
            return true;
        }
    }
}
