package com.cinema.modules.cinema.dto.response;

import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;

public record HallInfoResponse(
        Long id, Long cinemaId, String name, ScreenType screenType, HallStatus status, boolean deleted) {
}
