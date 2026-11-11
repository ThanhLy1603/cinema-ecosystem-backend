package com.cinema.modules.cinema.service;

import com.cinema.common.cache.VersionedRedisCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PublicCinemaCache extends VersionedRedisCache {

    static final Duration CACHE_TTL = Duration.ofHours(1);
    static final String KEY_PREFIX = "cinema:public:";
    static final String VERSION_KEY = KEY_PREFIX + "version";

    public PublicCinemaCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        super(stringRedisTemplate, objectMapper, KEY_PREFIX, CACHE_TTL, "danh mục rạp");
    }
}
