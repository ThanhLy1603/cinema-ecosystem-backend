package com.cinema.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.booking")
public record BookingPolicyProperties(@DefaultValue("30") int lateBookingMinutes) {

    public Duration lateBookingWindow() {
        return Duration.ofMinutes(Math.max(0, lateBookingMinutes));
    }
}
