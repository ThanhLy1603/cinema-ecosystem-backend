package com.cinema.modules.movie.service;

import com.cinema.common.cache.VersionedRedisCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PublicMovieCache extends VersionedRedisCache {

    static final Duration CACHE_TTL = Duration.ofMinutes(30);
    static final String KEY_PREFIX = "movie:public:";
    static final String VERSION_KEY = KEY_PREFIX + "version";

    public PublicMovieCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        super(stringRedisTemplate, objectMapper, KEY_PREFIX, CACHE_TTL, "danh mục phim");
    }
}
