package com.cinema.modules.auth.dto.response;

public record RefreshTokenResponse(String accessToken, UserResponse user) {
}
