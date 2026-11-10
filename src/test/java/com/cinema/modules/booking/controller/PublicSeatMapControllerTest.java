package com.cinema.modules.booking.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.booking.dto.response.ShowtimeSeatMapResponse;
import com.cinema.modules.booking.dto.response.ShowtimeSeatResponse;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.service.interfaces.IShowtimeSeatMapService;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicSeatMapControllerTest {

    @Mock
    private IShowtimeSeatMapService showtimeSeatMapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicSeatMapController(showtimeSeatMapService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsSeatsWithStateAndPrice() throws Exception {
        ShowtimeSeatResponse showtimeSeatResponse =
                new ShowtimeSeatResponse(11L, "A", 1, "STANDARD", 1, new BigDecimal("100000"), SeatAvailability.HELD);
        when(showtimeSeatMapService.getSeatMap(5L)).thenReturn(new ShowtimeSeatMapResponse(
                5L, 7L, "phim", "Phim", null, AgeRating.C13, 120, 1L, "Rạp A", 10L, "Phòng 1", ScreenType.SCREEN_2D,
                Instant.parse("2026-11-12T11:00:00Z"), Instant.parse("2026-11-12T13:00:00Z"),
                Instant.parse("2026-11-10T03:00:00Z"), List.of("A"), 1, List.of(showtimeSeatResponse)));

        mockMvc.perform(get("/api/v1/showtimes/public/5/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showtimeId").value(5))
                .andExpect(jsonPath("$.data.cinemaName").value("Rạp A"))
                .andExpect(jsonPath("$.data.seats[0].state").value("HELD"))
                .andExpect(jsonPath("$.data.seats[0].price").value(100000));
    }

    @Test
    void startedShowtimeMapsToConflict() throws Exception {
        when(showtimeSeatMapService.getSeatMap(5L))
                .thenThrow(new AppException("Suất chiếu đã bắt đầu", HttpStatus.CONFLICT, "SHOWTIME_STARTED"));

        mockMvc.perform(get("/api/v1/showtimes/public/5/seat-map"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHOWTIME_STARTED"));
    }
}
