package com.cinema.modules.cinema.dto.response;

import com.cinema.modules.cinema.enums.CinemaStatus;

import java.math.BigDecimal;
import java.util.List;

public record CinemaResponse(
        Long id,
        String name,
        String slug,
        String city,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        CinemaStatus status,
        List<FacilityResponse> facilities,
        int hallCount,
        boolean deleted) {
}
