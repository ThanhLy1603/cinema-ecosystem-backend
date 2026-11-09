package com.cinema.modules.cinema.dto.response;

import java.math.BigDecimal;

public record SeatTypeResponse(Long id, String code, String name, BigDecimal defaultSurcharge, int span) {
}
