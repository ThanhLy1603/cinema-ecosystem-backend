package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.CheckInResultCode;

public record TicketScanResponse(
        CheckInResultCode resultCode,
        String message,
        boolean canCheckIn,
        int pendingTicketCount,
        ScanBookingResponse booking) {
}
