package com.cinema.modules.cinema.dto.response;

import java.math.BigDecimal;

public record SeatPricingInfoResponse(
        Long seatId, Long hallId, String seatTypeCode, String seatTypeName, BigDecimal surcharge, boolean bookable) {
}
