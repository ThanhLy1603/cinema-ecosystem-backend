package com.cinema.common.util;

import java.time.Duration;
import java.time.Instant;

public final class BookingWindow {

    private BookingWindow() {
    }

    public static Instant earliestBookableStart(Instant now, Duration lateBookingWindow) {
        return now.minus(lateBookingWindow);
    }

    public static Instant lastBookableMoment(Instant showtimeStart, Duration lateBookingWindow) {
        return showtimeStart.plus(lateBookingWindow);
    }

    public static boolean isOpen(Instant showtimeStart, Instant now, Duration lateBookingWindow) {
        return !now.isAfter(lastBookableMoment(showtimeStart, lateBookingWindow));
    }
}
