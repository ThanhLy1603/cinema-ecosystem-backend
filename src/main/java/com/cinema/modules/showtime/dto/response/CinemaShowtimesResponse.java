package com.cinema.modules.showtime.dto.response;

import java.util.List;

public record CinemaShowtimesResponse(Long cinemaId, String cinemaName, List<BookableShowtimeResponse> showtimes) {
}
