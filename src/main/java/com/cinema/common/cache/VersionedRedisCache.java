package com.cinema.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public abstract class VersionedRedisCache {

    private static final String DEFAULT_VERSION = "0";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final String versionKey;
    private final Duration timeToLive;
    private final String label;

    protected VersionedRedisCache(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            String keyPrefix,
            Duration timeToLive,
            String label) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.versionKey = keyPrefix + "version";
        this.timeToLive = timeToLive;
        this.label = label;
    }

    public <T> T getOrLoad(String name, TypeReference<T> typeReference, Supplier<T> loader) {
        String cacheKey = null;
        try {
            cacheKey = keyPrefix + readVersion() + ":" + name;
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, typeReference);
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Không đọc được cache {} '{}': {}", label, name, exception.getMessage());
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

    public void evict() {
        try {
            stringRedisTemplate.opsForValue().increment(versionKey);
        } catch (RuntimeException runtimeException) {
            log.warn("Không làm mới được cache {}: {}", label, runtimeException.getMessage());
        }
    }

    private String readVersion() {
        String version = stringRedisTemplate.opsForValue().get(versionKey);
        return version == null ? DEFAULT_VERSION : version;
    }

    private <T> void writeCache(String cacheKey, T value) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), timeToLive);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Không ghi được cache {} '{}': {}", label, cacheKey, exception.getMessage());
        }
    }
}
