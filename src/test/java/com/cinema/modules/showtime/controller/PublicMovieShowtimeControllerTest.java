package com.cinema.modules.showtime.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.dto.response.BookableShowtimeResponse;
import com.cinema.modules.showtime.dto.response.CinemaShowtimesResponse;
import com.cinema.modules.showtime.dto.response.MovieShowtimeDatesResponse;
import com.cinema.modules.showtime.service.interfaces.IMovieShowtimeScheduleService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicMovieShowtimeControllerTest {

    @Mock
    private IMovieShowtimeScheduleService movieShowtimeScheduleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMovieShowtimeController(movieShowtimeScheduleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void datesEndpointReturnsIsoDates() throws Exception {
        when(movieShowtimeScheduleService.findShowtimeDates(7L, YearMonth.of(2026, 11), null))
                .thenReturn(new MovieShowtimeDatesResponse(7L, List.of(LocalDate.of(2026, 11, 12))));

        mockMvc.perform(get("/api/v1/showtimes/public/movies/7/dates").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movieId").value(7))
                .andExpect(jsonPath("$.data.dates[0]").value("2026-11-12"));
    }

    @Test
    void showtimesEndpointReturnsCinemaGroupsWithSeatAvailability() throws Exception {
        BookableShowtimeResponse bookableShowtimeResponse = new BookableShowtimeResponse(
                100L, 10L, "Phòng 1", ScreenType.SCREEN_2D, Instant.parse("2026-11-12T11:00:00Z"),
                Instant.parse("2026-11-12T13:00:00Z"), new BigDecimal("90000"), 100, 0, true);
        when(movieShowtimeScheduleService.findShowtimesByDate(7L, LocalDate.of(2026, 11, 12), 1L))
                .thenReturn(List.of(new CinemaShowtimesResponse(1L, "Rạp A", List.of(bookableShowtimeResponse))));

        mockMvc.perform(get("/api/v1/showtimes/public/movies/7/showtimes")
                        .param("date", "2026-11-12")
                        .param("cinemaId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cinemaName").value("Rạp A"))
                .andExpect(jsonPath("$.data[0].showtimes[0].id").value(100))
                .andExpect(jsonPath("$.data[0].showtimes[0].availableSeats").value(0))
                .andExpect(jsonPath("$.data[0].showtimes[0].soldOut").value(true));
    }

    @Test
    void unknownMovieMapsToNotFound() throws Exception {
        when(movieShowtimeScheduleService.findShowtimeDates(9L, YearMonth.of(2026, 11), null))
                .thenThrow(new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/showtimes/public/movies/9/dates").param("month", "2026-11"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MOVIE_NOT_FOUND"));
    }

    @Test
    void invalidMonthIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/showtimes/public/movies/7/dates").param("month", "2026-13"))
                .andExpect(status().isBadRequest());
    }
}
