package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDetailResponse(
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant serverTime,
        BookingShowtimeResponse showtime,
        List<BookingSeatResponse> seats,
        List<BookingFnbLineResponse> fnbLines,
        BigDecimal ticketAmount,
        BigDecimal fnbAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal finalAmount,
        List<BookingTicketResponse> tickets,
        String qrCodeDataUrl) {
}
