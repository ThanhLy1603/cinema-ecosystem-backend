package com.cinema.modules.booking.event;

import java.math.BigDecimal;

public record BookingRefundedEvent(Long bookingId, Long userId, String bookingCode, BigDecimal amount) {
}
