package com.cinema.modules.promotion.service;

import com.cinema.modules.promotion.dto.response.PublicPromotionResponse;
import com.cinema.modules.promotion.entity.Promotion;
import com.cinema.modules.promotion.enums.PromotionStatus;
import com.cinema.modules.promotion.repository.PromotionRepository;
import com.cinema.modules.promotion.service.interfaces.IPromotionPublicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PromotionPublicService implements IPromotionPublicService {

    private final PromotionRepository promotionRepository;
    private final Clock clock;

    public PromotionPublicService(PromotionRepository promotionRepository, Clock clock) {
        this.promotionRepository = promotionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public List<PublicPromotionResponse> findRunningPromotions() {
        return promotionRepository.findRunningPromotions(PromotionStatus.ACTIVE, Instant.now(clock)).stream()
                .map(this::toPublicPromotionResponse)
                .toList();
    }

    private PublicPromotionResponse toPublicPromotionResponse(Promotion promotion) {
        return new PublicPromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getTitle(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMaxDiscountAmount(),
                promotion.getMinOrderAmount(),
                promotion.getStartDate(),
                promotion.getEndDate());
    }
}
