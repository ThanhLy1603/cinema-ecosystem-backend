package com.cinema.modules.booking.dto.response;

import java.util.List;

public record TicketQueueBookingResponse(
        String bookingCode, int pendingTicketCount, int totalTicketCount, List<String> pendingSeatLabels) {
}
