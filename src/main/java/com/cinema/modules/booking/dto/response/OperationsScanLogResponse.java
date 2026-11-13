package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.enums.TicketScanAction;

import java.time.Instant;

public record OperationsScanLogResponse(
        Long id,
        String bookingCode,
        String scannedCode,
        TicketScanAction action,
        CheckInResultCode resultCode,
        int ticketCount,
        Long staffUserId,
        String staffName,
        Long cinemaId,
        String cinemaName,
        Instant createdAt) {
}
