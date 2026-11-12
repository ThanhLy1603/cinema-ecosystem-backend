package com.cinema.modules.community.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.community.dto.response.MovieRatingSummaryResponse;
import com.cinema.modules.community.service.interfaces.IMovieRatingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies/public/ratings")
public class PublicMovieRatingController {

    private final IMovieRatingService movieRatingService;

    public PublicMovieRatingController(IMovieRatingService movieRatingService) {
        this.movieRatingService = movieRatingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieRatingSummaryResponse>>> findSummaries(
            @RequestParam(name = "movieIds") List<Long> movieIds) {
        return ResponseEntity.ok(ApiResponse.success(movieRatingService.findSummaries(movieIds)));
    }
}
