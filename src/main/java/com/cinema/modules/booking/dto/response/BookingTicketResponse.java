package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingTicketResponse(
        Long ticketId,
        String ticketCode,
        Long seatId,
        String seatLabel,
        BigDecimal price,
        boolean checkedIn,
        Instant checkedInAt) {
}
