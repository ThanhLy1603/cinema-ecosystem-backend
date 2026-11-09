package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.request.CinemaRequest;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/admin/cinemas")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminCinemaController {

    private final ICinemaAdminService cinemaAdminService;

    public AdminCinemaController(ICinemaAdminService cinemaAdminService) {
        this.cinemaAdminService = cinemaAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CinemaResponse>>> searchCinemas(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) CinemaStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(cinemaAdminService.searchCinemas(
                authenticatedUser.userId(), search, city, status, page, size)));
    }

    @GetMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<CinemaDetailResponse>> getCinema(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(cinemaAdminService.getCinema(authenticatedUser.userId(), cinemaId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CinemaDetailResponse>> createCinema(
            @Valid @RequestBody CinemaRequest cinemaRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo cụm rạp thành công", cinemaAdminService.createCinema(cinemaRequest)));
    }

    @PutMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<CinemaDetailResponse>> updateCinema(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long cinemaId,
            @Valid @RequestBody CinemaRequest cinemaRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật cụm rạp thành công",
                cinemaAdminService.updateCinema(authenticatedUser.userId(), cinemaId, cinemaRequest)));
    }

    @DeleteMapping("/{cinemaId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCinema(@PathVariable Long cinemaId) {
        cinemaAdminService.deleteCinema(cinemaId);
        return ResponseEntity.ok(ApiResponse.success("Xóa cụm rạp thành công", null));
    }
}
