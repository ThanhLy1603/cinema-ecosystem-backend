package com.cinema.modules.cinema.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCinemaCacheTest {

    private static final TypeReference<List<String>> CITY_LIST = new TypeReference<>() { };

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PublicCinemaCache publicCinemaCache;

    @BeforeEach
    void setUp() {
        publicCinemaCache = new PublicCinemaCache(stringRedisTemplate, new ObjectMapper());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void cacheHitSkipsTheLoader() {
        when(valueOperations.get(PublicCinemaCache.VERSION_KEY)).thenReturn("7");
        when(valueOperations.get("cinema:public:7:cities")).thenReturn("[\"Hà Nội\"]");
        AtomicInteger loaderCalls = new AtomicInteger();

        List<String> cities = publicCinemaCache.getOrLoad("cities", CITY_LIST, () -> {
            loaderCalls.incrementAndGet();
            return List.of();
        });

        assertEquals(List.of("Hà Nội"), cities);
        assertEquals(0, loaderCalls.get());
    }

    @Test
    void cacheMissLoadsAndStoresForOneHour() {
        when(valueOperations.get(PublicCinemaCache.VERSION_KEY)).thenReturn(null);
        when(valueOperations.get("cinema:public:0:cities")).thenReturn(null);

        List<String> cities = publicCinemaCache.getOrLoad("cities", CITY_LIST, () -> List.of("Đà Nẵng"));

        assertEquals(List.of("Đà Nẵng"), cities);
        verify(valueOperations).set("cinema:public:0:cities", "[\"Đà Nẵng\"]", PublicCinemaCache.CACHE_TTL);
    }

    @Test
    void redisFailureFallsBackToTheLoader() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        List<String> cities = publicCinemaCache.getOrLoad("cities", CITY_LIST, () -> List.of("Huế"));

        assertEquals(List.of("Huế"), cities);
        verify(valueOperations, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    void evictBumpsTheVersionKey() {
        publicCinemaCache.evict();

        verify(valueOperations).increment(PublicCinemaCache.VERSION_KEY);
    }
}
