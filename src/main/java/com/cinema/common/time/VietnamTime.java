package com.cinema.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class VietnamTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private VietnamTime() {
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
