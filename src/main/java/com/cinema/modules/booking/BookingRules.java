package com.cinema.modules.booking;

import java.time.Duration;

public final class BookingRules {

    public static final Duration SEAT_HOLD_DURATION = Duration.ofMinutes(10);
    public static final int MAX_SEATS_PER_BOOKING = 8;

    private BookingRules() {
    }
}
