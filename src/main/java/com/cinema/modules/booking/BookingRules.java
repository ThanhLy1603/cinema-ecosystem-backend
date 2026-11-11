package com.cinema.modules.booking;

import java.time.Duration;

public final class BookingRules {

    public static final Duration SEAT_HOLD_DURATION = Duration.ofMinutes(10);
    public static final int MAX_SEATS_PER_BOOKING = 8;
    public static final int MAX_FNB_QUANTITY_PER_LINE = 10;
    public static final int MAX_FNB_LINES_PER_BOOKING = 40;

    private BookingRules() {
    }
}
