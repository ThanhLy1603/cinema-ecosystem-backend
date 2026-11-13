package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.enums.TicketScanAction;

import java.time.Instant;

public record TicketScanLogResponse(
        Long id,
        String bookingCode,
        TicketScanAction action,
        CheckInResultCode resultCode,
        int ticketCount,
        String staffName,
        Instant createdAt) {
}
