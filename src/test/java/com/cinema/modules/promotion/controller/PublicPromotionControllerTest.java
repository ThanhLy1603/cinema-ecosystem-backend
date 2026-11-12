package com.cinema.modules.promotion.controller;

import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.promotion.dto.response.PublicPromotionResponse;
import com.cinema.modules.promotion.enums.DiscountType;
import com.cinema.modules.promotion.service.interfaces.IPromotionPublicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicPromotionControllerTest {

    @Mock
    private IPromotionPublicService promotionPublicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicPromotionController(promotionPublicService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsRunningPromotionsInsideTheApiResponseEnvelope() throws Exception {
        when(promotionPublicService.findRunningPromotions()).thenReturn(List.of(new PublicPromotionResponse(
                1L, "TUESDAY20", "Thứ 3 giảm 20%", DiscountType.PERCENT, new BigDecimal("20.00"), null,
                new BigDecimal("100000.00"), Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/promotions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("TUESDAY20"))
                .andExpect(jsonPath("$.data[0].discountType").value("PERCENT"))
                .andExpect(jsonPath("$.data[0].discountValue").value(20.0));
    }
}
