package com.cinema.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.receipt")
public record ReceiptProperties(String directory) {
}
