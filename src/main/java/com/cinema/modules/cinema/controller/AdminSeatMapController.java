package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.request.GenerateSeatsRequest;
import com.cinema.modules.cinema.dto.request.SeatMapRequest;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.service.interfaces.ISeatMapService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/halls/{hallId}")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminSeatMapController {

    private final ISeatMapService seatMapService;

    public AdminSeatMapController(ISeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/seat-map")
    public ResponseEntity<ApiResponse<SeatLayoutResponse>> getSeatMap(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long hallId) {
        return ResponseEntity.ok(ApiResponse.success(seatMapService.getSeatMap(authenticatedUser.userId(), hallId)));
    }

    @PutMapping("/seat-map")
    public ResponseEntity<ApiResponse<SeatLayoutResponse>> saveSeatMap(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long hallId,
            @Valid @RequestBody SeatMapRequest seatMapRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lưu sơ đồ ghế thành công", seatMapService.saveSeatMap(authenticatedUser.userId(), hallId, seatMapRequest)));
    }

    @PostMapping("/generate-seats")
    public ResponseEntity<ApiResponse<SeatLayoutResponse>> generateSeats(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long hallId,
            @Valid @RequestBody GenerateSeatsRequest generateSeatsRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Sinh sơ đồ ghế thành công",
                seatMapService.generateSeats(authenticatedUser.userId(), hallId, generateSeatsRequest)));
    }
}
