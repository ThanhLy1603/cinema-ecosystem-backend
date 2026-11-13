package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ScanTicketResponse(
        Long ticketId,
        String seatLabel,
        String seatTypeName,
        String seatTypeCode,
        BigDecimal price,
        boolean checkedIn,
        Instant checkedInAt) {
}
