package com.cinema.modules.auth.security;

import com.cinema.config.CookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieFactoryTest {

    @Test
    void createdCookieIsHttpOnlySameSiteStrictAndScopedToAuthPath() {
        ResponseCookie responseCookie = new RefreshTokenCookieFactory(new CookieProperties(true))
                .create("raw-token", Duration.ofDays(7));

        assertEquals("refresh_token", responseCookie.getName());
        assertEquals("raw-token", responseCookie.getValue());
        assertTrue(responseCookie.isHttpOnly());
        assertTrue(responseCookie.isSecure());
        assertEquals("Strict", responseCookie.getSameSite());
        assertEquals("/api/v1/auth", responseCookie.getPath());
        assertEquals(Duration.ofDays(7), responseCookie.getMaxAge());
    }

    @Test
    void secureFlagFollowsConfiguration() {
        ResponseCookie responseCookie = new RefreshTokenCookieFactory(new CookieProperties(false))
                .create("raw-token", Duration.ofDays(7));

        assertFalse(responseCookie.isSecure());
        assertTrue(responseCookie.isHttpOnly());
    }

    @Test
    void clearedCookieExpiresImmediatelyWithSameScope() {
        ResponseCookie responseCookie = new RefreshTokenCookieFactory(new CookieProperties(true)).clear();

        assertEquals("", responseCookie.getValue());
        assertEquals(Duration.ZERO, responseCookie.getMaxAge());
        assertEquals("/api/v1/auth", responseCookie.getPath());
        assertTrue(responseCookie.isHttpOnly());
    }
}
