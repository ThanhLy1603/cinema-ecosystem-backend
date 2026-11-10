package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.dto.response.PriceRuleResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.repository.PriceRuleRepository;
import com.cinema.modules.showtime.service.interfaces.IPriceRuleAdminService;
import com.cinema.modules.showtime.util.PriceRuleMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PriceRuleAdminService implements IPriceRuleAdminService {

    private static final String INVALID_RULE_ERROR_CODE = "INVALID_PRICE_RULE";

    private final PriceRuleRepository priceRuleRepository;
    private final Clock clock;

    public PriceRuleAdminService(PriceRuleRepository priceRuleRepository, Clock clock) {
        this.priceRuleRepository = priceRuleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public List<PriceRuleResponse> listPriceRules(boolean deleted) {
        return priceRuleRepository.findByDeletedOrderByRuleNameAsc(deleted).stream()
                .map(PriceRuleMapper::toPriceRuleResponse)
                .toList();
    }

    @Transactional
    @Override
    public PriceRuleResponse createPriceRule(PriceRuleRequest priceRuleRequest) {
        String ruleName = priceRuleRequest.ruleName().trim();
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse(ruleName)) {
            throw nameAlreadyUsed();
        }
        PriceRule priceRule = new PriceRule();
        applyRequest(priceRule, priceRuleRequest, ruleName);
        return savePriceRule(priceRule);
    }

    @Transactional
    @Override
    public PriceRuleResponse updatePriceRule(Long priceRuleId, PriceRuleRequest priceRuleRequest) {
        PriceRule priceRule = loadPriceRule(priceRuleId, false);
        String ruleName = priceRuleRequest.ruleName().trim();
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalseAndIdNot(ruleName, priceRuleId)) {
            throw nameAlreadyUsed();
        }
        applyRequest(priceRule, priceRuleRequest, ruleName);
        return savePriceRule(priceRule);
    }

    @Transactional
    @Override
    public void deletePriceRule(Long priceRuleId) {
        PriceRule priceRule = loadPriceRule(priceRuleId, false);
        priceRule.markDeleted(Instant.now(clock));
        priceRuleRepository.save(priceRule);
    }

    @Transactional
    @Override
    public PriceRuleResponse restorePriceRule(Long priceRuleId) {
        PriceRule priceRule = loadPriceRule(priceRuleId, true);
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse(priceRule.getRuleName())) {
            throw nameAlreadyUsed();
        }
        priceRule.restore();
        return savePriceRule(priceRule);
    }

    private void applyRequest(PriceRule priceRule, PriceRuleRequest priceRuleRequest, String ruleName) {
        if (priceRuleRequest.validFrom() != null && priceRuleRequest.validTo() != null
                && priceRuleRequest.validTo().isBefore(priceRuleRequest.validFrom())) {
            throw invalidRule("Ngày kết thúc hiệu lực phải sau hoặc bằng ngày bắt đầu");
        }
        priceRule.setRuleName(ruleName);
        priceRule.setSurchargeAmount(priceRuleRequest.surchargeAmount());
        priceRule.setValidFrom(priceRuleRequest.validFrom());
        priceRule.setValidTo(priceRuleRequest.validTo());
        priceRule.setDayType(PriceDayType.ALL);
        priceRule.setTimeFrom(null);
        priceRule.setTimeTo(null);
        priceRule.setScreenType(null);
        switch (priceRuleRequest.ruleType()) {
            case DAY_TYPE -> applyDayType(priceRule, priceRuleRequest);
            case TIME_SLOT -> applyTimeSlot(priceRule, priceRuleRequest);
            case SCREEN_TYPE -> applyScreenType(priceRule, priceRuleRequest);
        }
    }

    private void applyDayType(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        PriceDayType priceDayType = priceRuleRequest.dayType();
        if (priceDayType == null || priceDayType == PriceDayType.ALL) {
            throw invalidRule("Vui lòng chọn loại ngày: ngày thường, cuối tuần hoặc ngày lễ");
        }
        if (priceDayType == PriceDayType.HOLIDAY
                && (priceRuleRequest.validFrom() == null || priceRuleRequest.validTo() == null)) {
            throw invalidRule("Phụ thu ngày lễ cần khoảng ngày áp dụng (từ ngày, đến ngày)");
        }
        priceRule.setDayType(priceDayType);
    }

    private void applyTimeSlot(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        if (priceRuleRequest.timeFrom() == null || priceRuleRequest.timeTo() == null) {
            throw invalidRule("Vui lòng nhập giờ bắt đầu và giờ kết thúc của khung giờ");
        }
        if (priceRuleRequest.timeFrom().equals(priceRuleRequest.timeTo())) {
            throw invalidRule("Giờ bắt đầu và giờ kết thúc của khung giờ không được trùng nhau");
        }
        priceRule.setTimeFrom(priceRuleRequest.timeFrom());
        priceRule.setTimeTo(priceRuleRequest.timeTo());
    }

    private void applyScreenType(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        if (priceRuleRequest.screenType() == null) {
            throw invalidRule("Vui lòng chọn loại phòng chiếu");
        }
        priceRule.setScreenType(priceRuleRequest.screenType());
    }

    private PriceRule loadPriceRule(Long priceRuleId, boolean deleted) {
        return priceRuleRepository.findById(priceRuleId)
                .filter(priceRule -> priceRule.isDeleted() == deleted)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy quy tắc giá", HttpStatus.NOT_FOUND, "PRICE_RULE_NOT_FOUND"));
    }

    private PriceRuleResponse savePriceRule(PriceRule priceRule) {
        try {
            return PriceRuleMapper.toPriceRuleResponse(priceRuleRepository.saveAndFlush(priceRule));
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên quy tắc giá đã tồn tại", HttpStatus.CONFLICT, "PRICE_RULE_NAME_EXISTS");
    }

    private static AppException invalidRule(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, INVALID_RULE_ERROR_CODE);
    }
}
