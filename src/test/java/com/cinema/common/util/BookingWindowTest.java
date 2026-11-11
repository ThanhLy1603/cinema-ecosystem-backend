package com.cinema.common.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingWindowTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(30);

    @Test
    void upcomingShowtimeIsOpen() {
        assertTrue(BookingWindow.isOpen(NOW.plusSeconds(3600), NOW, WINDOW));
    }

    @Test
    void showtimeStartedWithinTheWindowIsStillOpen() {
        assertTrue(BookingWindow.isOpen(NOW.minus(Duration.ofMinutes(29)), NOW, WINDOW));
    }

    @Test
    void showtimeIsOpenExactlyAtTheEndOfTheWindow() {
        assertTrue(BookingWindow.isOpen(NOW.minus(WINDOW), NOW, WINDOW));
    }

    @Test
    void showtimeStartedBeyondTheWindowIsClosed() {
        assertFalse(BookingWindow.isOpen(NOW.minus(Duration.ofMinutes(31)), NOW, WINDOW));
    }

    @Test
    void zeroWindowMeansBookingClosesWhenTheShowtimeStarts() {
        assertFalse(BookingWindow.isOpen(NOW.minusSeconds(1), NOW, Duration.ZERO));
        assertTrue(BookingWindow.isOpen(NOW.plusSeconds(1), NOW, Duration.ZERO));
    }

    @Test
    void earliestBookableStartAndLastBookableMomentMirrorTheWindow() {
        assertEquals(NOW.minus(WINDOW), BookingWindow.earliestBookableStart(NOW, WINDOW));
        assertEquals(NOW.plus(WINDOW), BookingWindow.lastBookableMoment(NOW, WINDOW));
    }
}
