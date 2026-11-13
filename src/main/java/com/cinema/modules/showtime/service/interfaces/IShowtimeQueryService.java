package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IShowtimeQueryService {

    Optional<ShowtimeInfoResponse> findShowtimeInfo(Long showtimeId);

    List<ShowtimeInfoResponse> findActiveShowtimeInfosBetween(Instant endsAfter, Instant startsBefore);
}
