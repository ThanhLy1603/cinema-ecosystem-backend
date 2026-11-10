package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.response.PublicMovieShowtimesResponse;

import java.time.LocalDate;
import java.util.List;

public interface IShowtimePublicService {

    List<PublicMovieShowtimesResponse> findShowtimes(Long cinemaId, LocalDate date, Long movieId);
}
