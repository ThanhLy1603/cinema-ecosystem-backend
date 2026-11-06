package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        List<String> roles,
        int loyaltyPoints,
        String tier,
        boolean emailVerified) {

    public static UserResponse from(User user) {
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                roleCodes,
                user.getLoyaltyPoints(),
                user.getTier().name(),
                user.isEmailVerified());
    }
}
