package com.cinema.modules.cinema.dto.response;

import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;

public record HallResponse(
        Long id, Long cinemaId, String name, ScreenType screenType, int totalSeats, HallStatus status, boolean deleted) {
}
