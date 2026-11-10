package com.cinema.modules.showtime.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.enums.ShowtimeConflictType;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.exception.ShowtimeExceptionHandler;
import com.cinema.modules.showtime.service.interfaces.IShowtimeAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminShowtimeControllerTest {

    private static final Long USER_ID = 5L;

    private static final ObjectMapper OBJECT_MAPPER =
            Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private IShowtimeAdminService showtimeAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminShowtimeController(showtimeAdminService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
                .setControllerAdvice(new ShowtimeExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "m@cinema.vn", List.of("ROLE_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controllerIsOpenToAdminAndManagerOnly() {
        PreAuthorize preAuthorize = AdminShowtimeController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')", preAuthorize.value());
    }

    @Test
    void createReturns409WithConflictDetailsForHighlighting() throws Exception {
        ShowtimeConflictResponse showtimeConflictResponse = new ShowtimeConflictResponse(
                40L, 8L, "Mai", Instant.parse("2026-01-02T11:00:00Z"), Instant.parse("2026-01-02T13:00:00Z"),
                Instant.parse("2026-01-02T13:15:00Z"), ShowtimeConflictType.CLEANING_TIME);
        when(showtimeAdminService.createShowtime(eq(USER_ID), any(ShowtimeRequest.class)))
                .thenThrow(new ShowtimeConflictException(List.of(showtimeConflictResponse)));

        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":9,"hallId":3,"startTime":"2026-01-02T13:05:00Z","basePrice":90000}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SHOWTIME_CONFLICT"))
                .andExpect(jsonPath("$.data[0].showtimeId").value(40))
                .andExpect(jsonPath("$.data[0].movieTitle").value("Mai"))
                .andExpect(jsonPath("$.data[0].conflictType").value("CLEANING_TIME"))
                .andExpect(jsonPath("$.data[0].occupiedUntil").value("2026-01-02T13:15:00Z"));
    }

    @Test
    void createRejectsMissingFieldsAndNegativePrice() throws Exception {
        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hallId":3,"basePrice":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.movieId").exists())
                .andExpect(jsonPath("$.data.startTime").exists())
                .andExpect(jsonPath("$.data.basePrice").exists());

        verifyNoInteractions(showtimeAdminService);
    }

    @Test
    void listPassesCinemaDateAndFiltersToService() throws Exception {
        when(showtimeAdminService.listShowtimes(USER_ID, 1L, LocalDate.of(2026, 1, 2), null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/showtimes").param("cinemaId", "1").param("date", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(showtimeAdminService).listShowtimes(USER_ID, 1L, LocalDate.of(2026, 1, 2), null, null);
    }

    @Test
    void listRequiresCinemaAndDate() throws Exception {
        mockMvc.perform(get("/api/v1/admin/showtimes")).andExpect(status().isBadRequest());

        verifyNoInteractions(showtimeAdminService);
    }
}
