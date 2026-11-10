package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;

import java.util.Optional;

public interface IShowtimeQueryService {

    Optional<ShowtimeInfoResponse> findShowtimeInfo(Long showtimeId);
}
