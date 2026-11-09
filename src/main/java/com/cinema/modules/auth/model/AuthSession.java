package com.cinema.modules.auth.model;

import com.cinema.modules.auth.dto.response.LoginResponse;
import com.cinema.modules.auth.dto.response.RefreshTokenResponse;
import com.cinema.modules.auth.dto.response.UserResponse;

import java.time.Duration;

public record AuthSession(
        UserResponse user,
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl) {

    public LoginResponse toLoginResponse() {
        return new LoginResponse(user);
    }

    public RefreshTokenResponse toRefreshTokenResponse() {
        return new RefreshTokenResponse(user);
    }

    @Override
    public String toString() {
        return "AuthSession[user=" + user + ", accessToken=***, refreshToken=***, accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
