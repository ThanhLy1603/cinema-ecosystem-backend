package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;

public record HeldSeatResponse(Long seatId, BigDecimal price) {
}
