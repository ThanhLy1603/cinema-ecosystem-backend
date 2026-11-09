package com.cinema.modules.cinema.dto.response;

import com.cinema.modules.cinema.enums.SeatStatus;

import java.math.BigDecimal;

public record SeatLayoutSeatResponse(
        Long id, String row, int number, String type, BigDecimal surcharge, int span, SeatStatus status) {
}
