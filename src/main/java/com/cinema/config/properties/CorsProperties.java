package com.cinema.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://127.0.0.1:3000"}) List<String> allowedOriginPatterns) {
}
