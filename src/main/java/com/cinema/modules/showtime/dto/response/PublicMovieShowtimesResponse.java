package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.movie.enums.AgeRating;

import java.util.List;

public record PublicMovieShowtimesResponse(
        Long movieId,
        String title,
        String posterUrl,
        AgeRating rated,
        int durationMinutes,
        List<PublicShowtimeResponse> showtimes) {
}
