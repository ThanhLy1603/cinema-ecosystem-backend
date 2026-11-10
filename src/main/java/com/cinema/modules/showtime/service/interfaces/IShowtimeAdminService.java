package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;

import java.time.LocalDate;
import java.util.List;

public interface IShowtimeAdminService {

    List<ShowtimeResponse> listShowtimes(
            Long actorUserId, Long cinemaId, LocalDate date, Long movieId, ShowtimeStatus showtimeStatus);

    ShowtimeResponse getShowtime(Long actorUserId, Long showtimeId);

    ShowtimeResponse createShowtime(Long actorUserId, ShowtimeRequest showtimeRequest);

    ShowtimeResponse updateShowtime(Long actorUserId, Long showtimeId, ShowtimeRequest showtimeRequest);

    ShowtimeResponse cancelShowtime(Long actorUserId, Long showtimeId);

    List<MovieInfoResponse> listSchedulableMovies();
}
