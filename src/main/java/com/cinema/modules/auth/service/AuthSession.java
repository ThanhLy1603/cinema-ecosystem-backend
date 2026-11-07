package com.cinema.modules.auth.service;

import com.cinema.modules.auth.dto.response.LoginResponse;
import com.cinema.modules.auth.dto.response.RefreshTokenResponse;
import com.cinema.modules.auth.dto.response.UserResponse;

import java.time.Duration;

public record AuthSession(UserResponse user, String accessToken, String refreshToken, Duration refreshTokenTtl) {

    public LoginResponse toLoginResponse() {
        return new LoginResponse(user, accessToken);
    }

    public RefreshTokenResponse toRefreshTokenResponse() {
        return new RefreshTokenResponse(accessToken, user);
    }

    @Override
    public String toString() {
        return "AuthSession[user=" + user + ", accessToken=***, refreshToken=***, refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
