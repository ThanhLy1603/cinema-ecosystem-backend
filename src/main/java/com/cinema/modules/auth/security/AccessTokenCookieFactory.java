package com.cinema.modules.auth.security;

import com.cinema.config.CookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AccessTokenCookieFactory {

    public static final String COOKIE_NAME = "access_token";
    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE = "Strict";

    private final boolean secure;

    public AccessTokenCookieFactory(CookieProperties cookieProperties) {
        this.secure = cookieProperties.secure();
    }

    public ResponseCookie create(String accessToken, Duration maxAge) {
        return build(accessToken, maxAge);
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
