package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.enums.MembershipTier;

public record CustomerLookupResponse(
        Long id, String fullName, String email, String phone, MembershipTier tier, int loyaltyPoints) {
}
