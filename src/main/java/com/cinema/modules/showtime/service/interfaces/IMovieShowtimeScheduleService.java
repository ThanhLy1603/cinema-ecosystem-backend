package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.response.CinemaShowtimesResponse;
import com.cinema.modules.showtime.dto.response.MovieShowtimeDatesResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface IMovieShowtimeScheduleService {

    MovieShowtimeDatesResponse findShowtimeDates(Long movieId, YearMonth yearMonth, Long cinemaId);

    List<CinemaShowtimesResponse> findShowtimesByDate(Long movieId, LocalDate date, Long cinemaId);
}
