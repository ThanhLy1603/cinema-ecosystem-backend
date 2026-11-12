package com.cinema.modules.promotion.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.promotion.dto.response.PublicPromotionResponse;
import com.cinema.modules.promotion.service.interfaces.IPromotionPublicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions/public")
public class PublicPromotionController {

    private final IPromotionPublicService promotionPublicService;

    public PublicPromotionController(IPromotionPublicService promotionPublicService) {
        this.promotionPublicService = promotionPublicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicPromotionResponse>>> getRunningPromotions() {
        return ResponseEntity.ok(ApiResponse.success(promotionPublicService.findRunningPromotions()));
    }
}
