package com.cinema.modules.showtime.dto.response;

import java.math.BigDecimal;

public record SeatTypePriceResponse(
        String seatTypeCode, String seatTypeName, BigDecimal seatSurcharge, BigDecimal totalPrice) {
}
