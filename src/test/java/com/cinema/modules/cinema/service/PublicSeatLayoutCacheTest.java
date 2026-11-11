package com.cinema.modules.cinema.service;

import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicSeatLayoutCacheTest {

    private static final TypeReference<SeatLayoutResponse> SEAT_LAYOUT = new TypeReference<>() { };

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void seatLayoutIsServedFromRedisWithoutCallingTheLoader() throws Exception {
        SeatLayoutResponse original = new SeatLayoutResponse(
                10L, 1L, "Phòng 1", ScreenType.SCREEN_2D, HallStatus.ACTIVE, 4L, List.of("A"), 2,
                List.of(new SeatLayoutSeatResponse(1L, "A", 1, "VIP", new BigDecimal("20000.00"), 1, SeatStatus.ACTIVE)));
        PublicCinemaCache publicCinemaCache = new PublicCinemaCache(stringRedisTemplate, objectMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PublicCinemaCache.VERSION_KEY)).thenReturn("2");
        when(valueOperations.get("cinema:public:2:seat-layout:10")).thenReturn(objectMapper.writeValueAsString(original));
        AtomicInteger loaderCalls = new AtomicInteger();

        SeatLayoutResponse cached = publicCinemaCache.getOrLoad("seat-layout:10", SEAT_LAYOUT, () -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        assertEquals(original, cached);
        assertEquals(0, loaderCalls.get());
    }
}
