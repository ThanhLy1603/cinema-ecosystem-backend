package com.cinema.modules.booking.event;

public record TicketReissuedEvent(Long bookingId, Long userId, String bookingCode) {
}
