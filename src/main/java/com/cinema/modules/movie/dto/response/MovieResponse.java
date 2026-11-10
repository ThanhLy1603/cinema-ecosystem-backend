package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

import java.time.LocalDate;
import java.util.List;

public record MovieResponse(
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
        MovieStatus status,
        List<GenreResponse> genres,
        boolean deleted) {
}
