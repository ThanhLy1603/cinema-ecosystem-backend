package com.cinema.modules.booking.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingQueryController {

    private final IBookingDetailService bookingDetailService;

    public BookingQueryController(IBookingDetailService bookingDetailService) {
        this.bookingDetailService = bookingDetailService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<BookingDetailResponse>> getPendingBooking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingDetailService.findPendingBooking(authenticatedUser.userId()).orElse(null)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<BookingDetailResponse>>> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(bookingDetailService.findMyBookings(authenticatedUser.userId())));
    }
}
