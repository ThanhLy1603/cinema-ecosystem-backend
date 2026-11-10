package com.cinema.modules.showtime.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IShowtimeAdminService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/showtimes")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminShowtimeController {

    private final IShowtimeAdminService showtimeAdminService;

    public AdminShowtimeController(IShowtimeAdminService showtimeAdminService) {
        this.showtimeAdminService = showtimeAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShowtimeResponse>>> listShowtimes(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Long cinemaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) ShowtimeStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                showtimeAdminService.listShowtimes(authenticatedUser.userId(), cinemaId, date, movieId, status)));
    }

    @GetMapping("/movie-options")
    public ResponseEntity<ApiResponse<List<MovieInfoResponse>>> listMovieOptions() {
        return ResponseEntity.ok(ApiResponse.success(showtimeAdminService.listSchedulableMovies()));
    }

    @GetMapping("/{showtimeId}")
    public ResponseEntity<ApiResponse<ShowtimeResponse>> getShowtime(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long showtimeId) {
        return ResponseEntity.ok(
                ApiResponse.success(showtimeAdminService.getShowtime(authenticatedUser.userId(), showtimeId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShowtimeResponse>> createShowtime(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ShowtimeRequest showtimeRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Tạo suất chiếu thành công",
                showtimeAdminService.createShowtime(authenticatedUser.userId(), showtimeRequest)));
    }

    @PutMapping("/{showtimeId}")
    public ResponseEntity<ApiResponse<ShowtimeResponse>> updateShowtime(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long showtimeId,
            @Valid @RequestBody ShowtimeRequest showtimeRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật suất chiếu thành công",
                showtimeAdminService.updateShowtime(authenticatedUser.userId(), showtimeId, showtimeRequest)));
    }

    @PostMapping("/{showtimeId}/cancel")
    public ResponseEntity<ApiResponse<ShowtimeResponse>> cancelShowtime(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long showtimeId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Hủy suất chiếu thành công", showtimeAdminService.cancelShowtime(authenticatedUser.userId(), showtimeId)));
    }
}
