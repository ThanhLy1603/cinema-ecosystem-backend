package com.cinema.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnpayProperties(
        @DefaultValue("") String tmnCode,
        @DefaultValue("") String hashSecret,
        @DefaultValue("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html") String payUrl,
        @DefaultValue("http://localhost:3000/payment/result") String returnUrl,
        @DefaultValue("15") int paymentWindowMinutes,
        @DefaultValue("false") boolean simulatorEnabled) {

    public boolean isConfigured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank();
    }
}
