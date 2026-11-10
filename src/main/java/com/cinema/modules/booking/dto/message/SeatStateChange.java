package com.cinema.modules.booking.dto.message;

import com.cinema.modules.booking.enums.SeatAvailability;

public record SeatStateChange(Long seatId, SeatAvailability state) {
}
