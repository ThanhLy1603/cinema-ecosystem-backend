package com.cinema.modules.cinema.dto.response;

public record SeatMapLockResponse(boolean locked, long upcomingShowtimeCount, long bookedShowtimeCount) {
}
