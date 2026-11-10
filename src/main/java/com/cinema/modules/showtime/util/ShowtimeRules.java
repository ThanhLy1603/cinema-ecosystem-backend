package com.cinema.modules.showtime.util;

import java.time.Duration;
import java.time.Instant;

public final class ShowtimeRules {

    public static final Duration CLEANING_DURATION = Duration.ofMinutes(15);

    private ShowtimeRules() {
    }

    public static Instant calculateEndTime(Instant startTime, int durationMinutes) {
        return startTime.plus(Duration.ofMinutes(durationMinutes));
    }

    public static Instant calculateOccupiedUntil(Instant endTime) {
        return endTime.plus(CLEANING_DURATION);
    }
}
