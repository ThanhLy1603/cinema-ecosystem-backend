package com.cinema.modules.cinema.dto.response;

import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;

import java.util.List;

public record SeatLayoutResponse(
        Long hallId,
        Long cinemaId,
        String hallName,
        ScreenType screenType,
        HallStatus hallStatus,
        long version,
        List<String> rows,
        int columns,
        List<SeatLayoutSeatResponse> seats) {
}
