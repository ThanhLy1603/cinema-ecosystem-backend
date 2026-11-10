package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMoviePublicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies/public")
public class PublicMovieController {

    private final IMoviePublicService moviePublicService;

    public PublicMovieController(IMoviePublicService moviePublicService) {
        this.moviePublicService = moviePublicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MovieResponse>>> searchMovies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(required = false) AgeRating rated,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                moviePublicService.searchMovies(keyword, genreId, status, rated, page, size)));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<GenreResponse>>> getGenres() {
        return ResponseEntity.ok(ApiResponse.success(moviePublicService.findGenres()));
    }

    @GetMapping("/slug/{movieSlug}")
    public ResponseEntity<ApiResponse<MovieDetailResponse>> getMovieBySlug(@PathVariable String movieSlug) {
        return ResponseEntity.ok(ApiResponse.success(moviePublicService.getMovieBySlug(movieSlug)));
    }
}
