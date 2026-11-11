package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;

public record BookingSeatResponse(Long seatId, String label, String seatTypeName, BigDecimal price) {
}
