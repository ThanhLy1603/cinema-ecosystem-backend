package com.cinema.modules.showtime.model;

import com.cinema.modules.showtime.util.ShowtimeRules;

import java.time.Instant;

public record PlannedShowtimeSlot(Instant startTime, Instant endTime) {

    public Instant occupiedUntil() {
        return ShowtimeRules.calculateOccupiedUntil(endTime);
    }
}
