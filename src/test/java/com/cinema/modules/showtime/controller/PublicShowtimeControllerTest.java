package com.cinema.modules.showtime.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.showtime.dto.response.PublicMovieShowtimesResponse;
import com.cinema.modules.showtime.dto.response.PublicShowtimeResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimePublicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicShowtimeControllerTest {

    private static final ObjectMapper OBJECT_MAPPER =
            Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private IShowtimePublicService showtimePublicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicShowtimeController(showtimePublicService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsShowtimesGroupedByMovie() throws Exception {
        PublicShowtimeResponse publicShowtimeResponse = new PublicShowtimeResponse(
                1L, 5L, "Phòng 01", ScreenType.IMAX, Instant.parse("2026-01-02T12:00:00Z"),
                Instant.parse("2026-01-02T14:00:00Z"), new BigDecimal("90000"));
        when(showtimePublicService.findShowtimes(1L, LocalDate.of(2026, 1, 2), 10L)).thenReturn(List.of(
                new PublicMovieShowtimesResponse(10L, "mai", "Mai", null, AgeRating.C16, 131, List.of(publicShowtimeResponse))));

        mockMvc.perform(get("/api/v1/showtimes/public")
                        .param("cinemaId", "1").param("date", "2026-01-02").param("movieId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Mai"))
                .andExpect(jsonPath("$.data[0].showtimes[0].screenType").value("IMAX"))
                .andExpect(jsonPath("$.data[0].showtimes[0].startTime").value("2026-01-02T12:00:00Z"));
    }

    @Test
    void requiresCinemaId() throws Exception {
        mockMvc.perform(get("/api/v1/showtimes/public")).andExpect(status().isBadRequest());

        verifyNoInteractions(showtimePublicService);
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/showtimes/public").param("cinemaId", "1").param("date", "02/01/2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(showtimePublicService);
    }
}
