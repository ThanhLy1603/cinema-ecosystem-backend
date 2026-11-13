package com.cinema.modules.booking.event;

public record CounterReceiptEmailRequestedEvent(Long bookingId, String email) {
}
