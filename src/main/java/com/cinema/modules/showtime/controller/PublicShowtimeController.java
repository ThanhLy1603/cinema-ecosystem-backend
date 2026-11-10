package com.cinema.modules.showtime.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.showtime.dto.response.PublicMovieShowtimesResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimePublicService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes/public")
public class PublicShowtimeController {

    private final IShowtimePublicService showtimePublicService;

    public PublicShowtimeController(IShowtimePublicService showtimePublicService) {
        this.showtimePublicService = showtimePublicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicMovieShowtimesResponse>>> findShowtimes(
            @RequestParam Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(showtimePublicService.findShowtimes(cinemaId, date, movieId)));
    }
}
