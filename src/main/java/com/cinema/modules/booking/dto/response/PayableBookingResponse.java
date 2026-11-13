package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PayableBookingResponse(
        Long bookingId, String bookingCode, Long cinemaId, BigDecimal finalAmount, Instant expiresAt) {
}
