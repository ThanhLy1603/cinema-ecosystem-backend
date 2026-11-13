package com.cinema.modules.staff.dto.response;

import com.cinema.modules.staff.enums.ShiftStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ShiftResponse(
        Long id,
        Long staffUserId,
        String staffName,
        Long cinemaId,
        String cinemaName,
        ShiftStatus status,
        Instant openedAt,
        Instant closedAt,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal cashDifference,
        String note,
        BigDecimal totalSales,
        long orderCount) {
}
