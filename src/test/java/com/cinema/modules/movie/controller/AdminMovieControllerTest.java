package com.cinema.modules.movie.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminMovieControllerTest {

    @Mock
    private IMovieAdminService movieAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminMovieController(movieAdminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201WithDetail() throws Exception {
        MovieDetailResponse movieDetailResponse = new MovieDetailResponse(
                1L, "Mai", null, 131, LocalDate.of(2026, 2, 10), null, AgeRating.C16, null, null, null, null,
                MovieStatus.NOW_SHOWING, List.of(), List.of(), false);
        when(movieAdminService.createMovie(any(MovieRequest.class))).thenReturn(movieDetailResponse);

        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Mai","durationMinutes":131,"releaseDate":"2026-02-10","rated":"C16",
                                 "status":"NOW_SHOWING","genreIds":[1],"casts":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void createRejectsInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","durationMinutes":0,"releaseDate":"2026-02-10","rated":"C16",
                                 "status":"NOW_SHOWING","posterUrl":"http://insecure/p.jpg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.durationMinutes").exists())
                .andExpect(jsonPath("$.data.posterUrl").exists());

        verify(movieAdminService, never()).createMovie(any());
    }

    @Test
    void createRejectsMissingAgeRating() throws Exception {
        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Mai","durationMinutes":100,"releaseDate":"2026-02-10","status":"NOW_SHOWING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.rated").exists());
    }
}
