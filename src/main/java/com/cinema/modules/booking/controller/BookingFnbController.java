package com.cinema.modules.booking.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.request.BookingFnbRequest;
import com.cinema.modules.booking.dto.response.BookingFnbResponse;
import com.cinema.modules.booking.service.interfaces.IBookingFnbService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings/pending/fnb")
public class BookingFnbController {

    private final IBookingFnbService bookingFnbService;

    public BookingFnbController(IBookingFnbService bookingFnbService) {
        this.bookingFnbService = bookingFnbService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BookingFnbResponse>> getPendingFnb(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(bookingFnbService.getPendingFnb(authenticatedUser.userId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BookingFnbResponse>> replacePendingFnb(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody BookingFnbRequest bookingFnbRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingFnbService.replacePendingFnb(authenticatedUser.userId(), bookingFnbRequest)));
    }
}
