package com.cinema.modules.promotion.service;

import com.cinema.modules.promotion.dto.response.PublicPromotionResponse;
import com.cinema.modules.promotion.entity.Promotion;
import com.cinema.modules.promotion.enums.DiscountType;
import com.cinema.modules.promotion.enums.PromotionStatus;
import com.cinema.modules.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPublicServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    @Mock
    private PromotionRepository promotionRepository;

    private PromotionPublicService promotionPublicService;

    @BeforeEach
    void setUp() {
        promotionPublicService = new PromotionPublicService(promotionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void mapsRunningPromotionsAndAsksTheRepositoryForActiveOnesAtTheCurrentInstant() {
        Promotion percentPromotion = new Promotion();
        percentPromotion.setId(1L);
        percentPromotion.setCode("TUESDAY20");
        percentPromotion.setTitle("Thứ 3 vui vẻ giảm 20%");
        percentPromotion.setDiscountType(DiscountType.PERCENT);
        percentPromotion.setDiscountValue(new BigDecimal("20.00"));
        percentPromotion.setMaxDiscountAmount(new BigDecimal("50000.00"));
        percentPromotion.setMinOrderAmount(new BigDecimal("100000.00"));
        percentPromotion.setStartDate(NOW.minusSeconds(3600));
        percentPromotion.setEndDate(NOW.plusSeconds(86400));
        Promotion fixedPromotion = new Promotion();
        fixedPromotion.setId(2L);
        fixedPromotion.setTitle("Giảm 30.000đ");
        fixedPromotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        fixedPromotion.setDiscountValue(new BigDecimal("30000.00"));
        fixedPromotion.setStartDate(NOW.minusSeconds(60));
        fixedPromotion.setEndDate(NOW.plusSeconds(60));
        when(promotionRepository.findRunningPromotions(PromotionStatus.ACTIVE, NOW))
                .thenReturn(List.of(percentPromotion, fixedPromotion));

        List<PublicPromotionResponse> publicPromotionResponses = promotionPublicService.findRunningPromotions();

        assertEquals(2, publicPromotionResponses.size());
        assertEquals("TUESDAY20", publicPromotionResponses.get(0).code());
        assertEquals(DiscountType.PERCENT, publicPromotionResponses.get(0).discountType());
        assertEquals(new BigDecimal("20.00"), publicPromotionResponses.get(0).discountValue());
        assertEquals(new BigDecimal("50000.00"), publicPromotionResponses.get(0).maxDiscountAmount());
        assertEquals(new BigDecimal("100000.00"), publicPromotionResponses.get(0).minOrderAmount());
        assertNull(publicPromotionResponses.get(1).code());
        assertNull(publicPromotionResponses.get(1).maxDiscountAmount());
        assertEquals(NOW.plusSeconds(60), publicPromotionResponses.get(1).endDate());
    }

    @Test
    void returnsAnEmptyListWhenNothingIsRunning() {
        when(promotionRepository.findRunningPromotions(PromotionStatus.ACTIVE, NOW)).thenReturn(List.of());

        assertEquals(List.of(), promotionPublicService.findRunningPromotions());
    }
}
