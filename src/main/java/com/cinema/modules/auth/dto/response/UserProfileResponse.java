package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.AuthProvider;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,
        String gender,
        List<String> roles,
        int loyaltyPoints,
        String tier,
        boolean emailVerified,
        boolean hasPassword,
        List<String> linkedProviders) {

    public static UserProfileResponse from(User user, List<AuthProvider> authProviders) {
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();
        List<String> providerNames = authProviders.stream().map(AuthProvider::name).sorted().toList();
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getDateOfBirth(),
                user.getGender() == null ? null : user.getGender().name(),
                roleCodes,
                user.getLoyaltyPoints(),
                user.getTier().name(),
                user.isEmailVerified(),
                user.hasPassword(),
                providerNames);
    }
}
