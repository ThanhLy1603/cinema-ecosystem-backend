package com.cinema.modules.booking.dto.response;

import java.util.List;

public record FnbPickupQueueBookingResponse(
        String bookingCode, int remainingQuantity, List<FnbPickupQueueLineResponse> lines) {
}
