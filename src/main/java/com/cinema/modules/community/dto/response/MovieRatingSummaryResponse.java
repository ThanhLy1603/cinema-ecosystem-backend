package com.cinema.modules.community.dto.response;

public record MovieRatingSummaryResponse(Long movieId, Double averageRating, Long reviewCount) {
}
