package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.SeatAvailability;

import java.math.BigDecimal;

public record ShowtimeSeatResponse(
        Long id, String row, int number, String type, int span, BigDecimal price, SeatAvailability state) {
}
