package com.cinema.modules.movie.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieCastResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMoviePublicService;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicMovieControllerTest {

    @Mock
    private IMoviePublicService moviePublicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMovieController(moviePublicService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void searchPassesFiltersAndExposesAgeRatingInCamelCase() throws Exception {
        MovieResponse movieResponse = new MovieResponse(
                1L, "Mai", null, 131, LocalDate.of(2026, 2, 10), null, AgeRating.C18, "https://img/p.jpg", null, null,
                MovieStatus.NOW_SHOWING, List.of(new GenreResponse(2L, "Tâm Lý", "tam-ly")), false);
        when(moviePublicService.searchMovies("mai", 2L, MovieStatus.NOW_SHOWING, AgeRating.C18, 0, 12))
                .thenReturn(new PageResponse<>(List.of(movieResponse), 0, 12, 1, 1));

        mockMvc.perform(get("/api/v1/movies/public")
                        .param("keyword", "mai")
                        .param("genreId", "2")
                        .param("status", "NOW_SHOWING")
                        .param("rated", "C18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rated").value("C18"))
                .andExpect(jsonPath("$.data.content[0].durationMinutes").value(131))
                .andExpect(jsonPath("$.data.content[0].releaseDate").value("2026-02-10"))
                .andExpect(jsonPath("$.data.content[0].genres[0].slug").value("tam-ly"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchRejectsUnknownAgeRating() throws Exception {
        mockMvc.perform(get("/api/v1/movies/public").param("rated", "C21")).andExpect(status().isBadRequest());
    }

    @Test
    void genresEndpointIsNotShadowedByMovieIdEndpoint() throws Exception {
        when(moviePublicService.findGenres()).thenReturn(List.of(new GenreResponse(1L, "Hành Động", "hanh-dong")));

        mockMvc.perform(get("/api/v1/movies/public/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Hành Động"));
    }

    @Test
    void detailIncludesCast() throws Exception {
        MovieDetailResponse movieDetailResponse = new MovieDetailResponse(
                1L, "Mai", null, 131, LocalDate.of(2026, 2, 10), null, AgeRating.C16, null, null, null, "Mô tả",
                MovieStatus.NOW_SHOWING, List.of(),
                List.of(new MovieCastResponse(5L, "Trấn Thành", null, CastRoleType.DIRECTOR, null)), false);
        when(moviePublicService.getMovie(1L)).thenReturn(movieDetailResponse);

        mockMvc.perform(get("/api/v1/movies/public/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Mô tả"))
                .andExpect(jsonPath("$.data.casts[0].roleType").value("DIRECTOR"))
                .andExpect(jsonPath("$.data.casts[0].personName").value("Trấn Thành"));
    }

    @Test
    void missingMovieReturnsNotFoundErrorCode() throws Exception {
        when(moviePublicService.getMovie(9L))
                .thenThrow(new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/movies/public/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MOVIE_NOT_FOUND"));
    }
}
