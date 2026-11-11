package com.cinema.modules.movie.service;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicMovieCacheTest {

    private static final TypeReference<PageResponse<MovieResponse>> MOVIE_PAGE = new TypeReference<>() { };

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private PublicMovieCache publicMovieCache;

    @BeforeEach
    void setUp() {
        publicMovieCache = new PublicMovieCache(stringRedisTemplate, objectMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void moviePageSurvivesTheRedisJsonRoundTrip() throws Exception {
        PageResponse<MovieResponse> original = new PageResponse<>(
                List.of(new MovieResponse(
                        7L, "Mai", "mai", "Mai", 120, LocalDate.of(2026, 11, 1), null, AgeRating.C13, "poster.jpg", null,
                        null, MovieStatus.NOW_SHOWING, List.of(new GenreResponse(1L, "Tình cảm", "tinh-cam")), false)),
                0, 12, 1, 1);
        when(valueOperations.get(PublicMovieCache.VERSION_KEY)).thenReturn("3");
        when(valueOperations.get("movie:public:3:list:x")).thenReturn(objectMapper.writeValueAsString(original));
        AtomicInteger loaderCalls = new AtomicInteger();

        PageResponse<MovieResponse> cached = publicMovieCache.getOrLoad("list:x", MOVIE_PAGE, () -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        assertEquals(original, cached);
        assertEquals(0, loaderCalls.get());
    }

    @Test
    void missStoresUnderTheMovieNamespaceWithThirtyMinuteTtl() {
        when(valueOperations.get(PublicMovieCache.VERSION_KEY)).thenReturn(null);
        when(valueOperations.get("movie:public:0:genres")).thenReturn(null);
        List<GenreResponse> loaded = List.of(new GenreResponse(1L, "Hài", "hai"));

        List<GenreResponse> genres = publicMovieCache.getOrLoad("genres", new TypeReference<>() { }, () -> loaded);

        assertEquals(loaded, genres);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("movie:public:0:genres"), jsonCaptor.capture(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void evictBumpsOnlyTheMovieVersionKey() {
        publicMovieCache.evict();

        verify(valueOperations).increment("movie:public:version");
    }
}
