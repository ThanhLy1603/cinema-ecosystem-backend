package com.cinema.modules.cinema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class PublicCinemaCache {

    static final Duration CACHE_TTL = Duration.ofHours(1);
    static final String KEY_PREFIX = "cinema:public:";
    static final String VERSION_KEY = KEY_PREFIX + "version";
    private static final String DEFAULT_VERSION = "0";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PublicCinemaCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String name, TypeReference<T> typeReference, Supplier<T> loader) {
        String cacheKey = null;
        try {
            cacheKey = KEY_PREFIX + readVersion() + ":" + name;
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, typeReference);
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Không đọc được cache danh mục rạp '{}': {}", name, exception.getMessage());
        }
        T loadedValue = loader.get();
        if (cacheKey != null) {
            writeCache(cacheKey, loadedValue);
        }
        return loadedValue;
    }

    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict();
            }
        });
    }

    void evict() {
        try {
            stringRedisTemplate.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException runtimeException) {
            log.warn("Không làm mới được cache danh mục rạp: {}", runtimeException.getMessage());
        }
    }

    private String readVersion() {
        String version = stringRedisTemplate.opsForValue().get(VERSION_KEY);
        return version == null ? DEFAULT_VERSION : version;
    }

    private <T> void writeCache(String cacheKey, T value) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Không ghi được cache danh mục rạp '{}': {}", cacheKey, exception.getMessage());
        }
    }
}
