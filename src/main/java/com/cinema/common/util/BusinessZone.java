package com.cinema.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class BusinessZone {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private BusinessZone() {
    }

    public static Instant startOfDay(LocalDate localDate) {
        return localDate.atStartOfDay(VIETNAM).toInstant();
    }

    public static Instant startOfNextDay(LocalDate localDate) {
        return localDate.plusDays(1).atStartOfDay(VIETNAM).toInstant();
    }

    public static Instant toInstant(LocalDate localDate, LocalTime localTime) {
        return localDate.atTime(localTime).atZone(VIETNAM).toInstant();
    }

    public static LocalTime toLocalTime(Instant instant) {
        return instant.atZone(VIETNAM).toLocalTime();
    }

    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(VIETNAM).toLocalDate();
    }
}
