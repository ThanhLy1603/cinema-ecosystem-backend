package com.cinema.modules.cinema.dto.response;

import java.math.BigDecimal;

public record SeatTypeSurchargeResponse(String code, String name, BigDecimal surcharge) {
}
