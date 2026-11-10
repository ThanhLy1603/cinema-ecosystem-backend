package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SeatHoldResponse(
        Long bookingId,
        String bookingCode,
        Long showtimeId,
        Instant expiresAt,
        Instant serverTime,
        List<HeldSeatResponse> seats,
        BigDecimal totalAmount) {
}
