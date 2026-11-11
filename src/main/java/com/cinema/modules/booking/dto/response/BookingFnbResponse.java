package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingFnbResponse(
        Long bookingId,
        Instant expiresAt,
        Instant serverTime,
        List<BookingFnbLineResponse> lines,
        BigDecimal ticketAmount,
        BigDecimal fnbAmount,
        BigDecimal totalAmount) {
}
