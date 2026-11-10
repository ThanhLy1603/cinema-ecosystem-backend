package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

import java.time.LocalDate;
import java.util.List;

public record MovieDetailResponse(
        Long id,
        String title,
        String originalTitle,
        int durationMinutes,
        LocalDate releaseDate,
        LocalDate endDate,
        AgeRating rated,
        String posterUrl,
        String bannerUrl,
        String trailerUrl,
        String description,
        MovieStatus status,
        List<GenreResponse> genres,
        List<MovieCastResponse> casts,
        boolean deleted) {
}
