package com.cinema.modules.auth.security;

import com.cinema.config.properties.CookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenCookieFactoryTest {

    @Test
    void createdCookieIsHttpOnlySameSiteStrictAndSentToAllApiPaths() {
        ResponseCookie responseCookie = new AccessTokenCookieFactory(new CookieProperties(true))
                .create("jwt-value", Duration.ofMinutes(15));

        assertEquals("access_token", responseCookie.getName());
        assertEquals("jwt-value", responseCookie.getValue());
        assertTrue(responseCookie.isHttpOnly());
        assertTrue(responseCookie.isSecure());
        assertEquals("Strict", responseCookie.getSameSite());
        assertEquals("/", responseCookie.getPath());
        assertEquals(Duration.ofMinutes(15), responseCookie.getMaxAge());
    }

    @Test
    void secureFlagFollowsConfiguration() {
        ResponseCookie responseCookie = new AccessTokenCookieFactory(new CookieProperties(false))
                .create("jwt-value", Duration.ofMinutes(15));

        assertFalse(responseCookie.isSecure());
        assertTrue(responseCookie.isHttpOnly());
    }

    @Test
    void clearedCookieExpiresImmediatelyWithSameScope() {
        ResponseCookie responseCookie = new AccessTokenCookieFactory(new CookieProperties(true)).clear();

        assertEquals("", responseCookie.getValue());
        assertEquals(Duration.ZERO, responseCookie.getMaxAge());
        assertEquals("/", responseCookie.getPath());
        assertTrue(responseCookie.isHttpOnly());
    }
}
