package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/movies")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminMovieController {

    private final IMovieAdminService movieAdminService;

    public AdminMovieController(IMovieAdminService movieAdminService) {
        this.movieAdminService = movieAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<ApiResponse<PageResponse<MovieResponse>>> searchMovies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(required = false) AgeRating rated,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                movieAdminService.searchMovies(keyword, genreId, status, rated, deleted, page, size)));
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieDetailResponse>> getMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(movieAdminService.getMovie(movieId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MovieDetailResponse>> createMovie(@Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phim thành công", movieAdminService.createMovie(movieRequest)));
    }

    @PutMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieDetailResponse>> updateMovie(
            @PathVariable Long movieId, @Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật phim thành công", movieAdminService.updateMovie(movieId, movieRequest)));
    }

    @DeleteMapping("/{movieId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMovie(@PathVariable Long movieId) {
        movieAdminService.deleteMovie(movieId);
        return ResponseEntity.ok(ApiResponse.success("Xóa phim thành công", null));
    }

    @PostMapping("/{movieId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<MovieDetailResponse>> restoreMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success("Khôi phục phim thành công", movieAdminService.restoreMovie(movieId)));
    }
}
