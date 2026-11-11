package com.cinema.modules.booking.event;

import java.math.BigDecimal;

public record BookingPaidEvent(Long bookingId, Long userId, String bookingCode, BigDecimal finalAmount) {
}
