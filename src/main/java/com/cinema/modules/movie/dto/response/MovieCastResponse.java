package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.CastRoleType;

public record MovieCastResponse(
        Long personId, String personName, String avatarUrl, CastRoleType roleType, String characterName) {
}
