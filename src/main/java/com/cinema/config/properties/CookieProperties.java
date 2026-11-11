package com.cinema.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure) {
}
