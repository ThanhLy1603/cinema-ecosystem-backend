package com.cinema.modules.auth.security;

import com.cinema.modules.auth.model.AuthSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieWriter {

    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final AccessTokenCookieFactory accessTokenCookieFactory;

    public SessionCookieWriter(
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            AccessTokenCookieFactory accessTokenCookieFactory) {
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.accessTokenCookieFactory = accessTokenCookieFactory;
    }

    public ResponseEntity.BodyBuilder okWithSessionCookies(AuthSession authSession) {
        String refreshCookie =
                refreshTokenCookieFactory.create(authSession.refreshToken(), authSession.refreshTokenTtl()).toString();
        String accessCookie =
                accessTokenCookieFactory.create(authSession.accessToken(), authSession.accessTokenTtl()).toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie)
                .header(HttpHeaders.SET_COOKIE, accessCookie);
    }

    public ResponseEntity.BodyBuilder okWithClearedCookies() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .header(HttpHeaders.SET_COOKIE, accessTokenCookieFactory.clear().toString());
    }

    public void addClearedCookies(HttpServletResponse httpServletResponse) {
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString());
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookieFactory.clear().toString());
    }
}
