package com.cinema.modules.booking.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.response.SeatHoldResponse;
import com.cinema.modules.booking.service.interfaces.ISeatHoldService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class SeatHoldController {

    private final ISeatHoldService seatHoldService;

    public SeatHoldController(ISeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping("/showtimes/{showtimeId}/seats/{seatId}/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeat(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long showtimeId,
            @PathVariable Long seatId) {
        return ResponseEntity.ok(ApiResponse.success(
                seatHoldService.holdSeat(authenticatedUser.userId(), showtimeId, seatId)));
    }

    @DeleteMapping("/showtimes/{showtimeId}/seats/{seatId}/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> releaseSeat(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long showtimeId,
            @PathVariable Long seatId) {
        return ResponseEntity.ok(ApiResponse.success(
                seatHoldService.releaseSeat(authenticatedUser.userId(), showtimeId, seatId)));
    }

    @GetMapping("/showtimes/{showtimeId}/my-hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> getMyHold(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long showtimeId) {
        return ResponseEntity.ok(ApiResponse.success(
                seatHoldService.getMyHold(authenticatedUser.userId(), showtimeId)));
    }

    @DeleteMapping("/pending")
    public ResponseEntity<ApiResponse<Void>> cancelPendingBooking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        seatHoldService.cancelPendingBooking(authenticatedUser.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
