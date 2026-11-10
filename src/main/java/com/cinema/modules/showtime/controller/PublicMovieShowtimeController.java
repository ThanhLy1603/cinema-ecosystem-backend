package com.cinema.modules.showtime.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.showtime.dto.response.CinemaShowtimesResponse;
import com.cinema.modules.showtime.dto.response.MovieShowtimeDatesResponse;
import com.cinema.modules.showtime.service.interfaces.IMovieShowtimeScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes/public/movies/{movieId}")
public class PublicMovieShowtimeController {

    private final IMovieShowtimeScheduleService movieShowtimeScheduleService;

    public PublicMovieShowtimeController(IMovieShowtimeScheduleService movieShowtimeScheduleService) {
        this.movieShowtimeScheduleService = movieShowtimeScheduleService;
    }

    @GetMapping("/dates")
    public ResponseEntity<ApiResponse<MovieShowtimeDatesResponse>> findShowtimeDates(
            @PathVariable Long movieId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                movieShowtimeScheduleService.findShowtimeDates(movieId, month, cinemaId)));
    }

    @GetMapping("/showtimes")
    public ResponseEntity<ApiResponse<List<CinemaShowtimesResponse>>> findShowtimesByDate(
            @PathVariable Long movieId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                movieShowtimeScheduleService.findShowtimesByDate(movieId, date, cinemaId)));
    }
}
