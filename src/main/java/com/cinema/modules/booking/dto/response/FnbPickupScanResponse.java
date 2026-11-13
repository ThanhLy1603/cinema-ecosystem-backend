package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.FnbPickupResultCode;

public record FnbPickupScanResponse(
        FnbPickupResultCode resultCode,
        String message,
        boolean canDeliver,
        FnbPickupBookingResponse booking) {
}
