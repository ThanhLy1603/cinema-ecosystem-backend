package com.cinema.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowsUpToTheMaximumNumberOfAttemptsThenBlocks() {
        when(valueOperations.increment("rate-limit:forgot:abc")).thenReturn(1L, 2L, 3L, 4L);

        assertTrue(rateLimiter.tryAcquire("forgot:abc", 3, WINDOW));
        assertTrue(rateLimiter.tryAcquire("forgot:abc", 3, WINDOW));
        assertTrue(rateLimiter.tryAcquire("forgot:abc", 3, WINDOW));
        assertFalse(rateLimiter.tryAcquire("forgot:abc", 3, WINDOW));
    }

    @Test
    void createsTheCounterWithItsExpiryInOneAtomicStep() {
        when(valueOperations.increment("rate-limit:k")).thenReturn(1L);

        rateLimiter.tryAcquire("k", 3, WINDOW);

        verify(valueOperations).setIfAbsent("rate-limit:k", "0", WINDOW);
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent("rate-limit:k", "0", WINDOW))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertTrue(rateLimiter.tryAcquire("k", 3, WINDOW));
    }
}
