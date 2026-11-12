package com.cinema.modules.promotion.service.interfaces;

import com.cinema.modules.promotion.dto.response.PublicPromotionResponse;

import java.util.List;

public interface IPromotionPublicService {

    List<PublicPromotionResponse> findRunningPromotions();
}
