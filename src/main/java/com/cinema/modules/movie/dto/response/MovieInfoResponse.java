package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

public record MovieInfoResponse(
        Long id,
        String title,
        int durationMinutes,
        AgeRating rated,
        String posterUrl,
        MovieStatus status,
        boolean deleted) {
}
