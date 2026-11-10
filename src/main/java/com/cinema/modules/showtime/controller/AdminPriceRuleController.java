package com.cinema.modules.showtime.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.dto.request.PriceSimulationRequest;
import com.cinema.modules.showtime.dto.response.PriceRuleResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IPriceRuleAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/price-rules")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminPriceRuleController {

    private final IPriceRuleAdminService priceRuleAdminService;
    private final IDynamicPricingService dynamicPricingService;

    public AdminPriceRuleController(
            IPriceRuleAdminService priceRuleAdminService, IDynamicPricingService dynamicPricingService) {
        this.priceRuleAdminService = priceRuleAdminService;
        this.dynamicPricingService = dynamicPricingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<ApiResponse<List<PriceRuleResponse>>> listPriceRules(
            @RequestParam(defaultValue = "false") boolean deleted) {
        return ResponseEntity.ok(ApiResponse.success(priceRuleAdminService.listPriceRules(deleted)));
    }

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<ShowtimePriceResponse>> simulatePrices(
            @Valid @RequestBody PriceSimulationRequest priceSimulationRequest) {
        return ResponseEntity.ok(ApiResponse.success(dynamicPricingService.simulatePrices(priceSimulationRequest)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PriceRuleResponse>> createPriceRule(
            @Valid @RequestBody PriceRuleRequest priceRuleRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Tạo quy tắc giá thành công", priceRuleAdminService.createPriceRule(priceRuleRequest)));
    }

    @PutMapping("/{priceRuleId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PriceRuleResponse>> updatePriceRule(
            @PathVariable Long priceRuleId, @Valid @RequestBody PriceRuleRequest priceRuleRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật quy tắc giá thành công", priceRuleAdminService.updatePriceRule(priceRuleId, priceRuleRequest)));
    }

    @DeleteMapping("/{priceRuleId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePriceRule(@PathVariable Long priceRuleId) {
        priceRuleAdminService.deletePriceRule(priceRuleId);
        return ResponseEntity.ok(ApiResponse.success("Xóa quy tắc giá thành công", null));
    }

    @PostMapping("/{priceRuleId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PriceRuleResponse>> restorePriceRule(@PathVariable Long priceRuleId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Khôi phục quy tắc giá thành công", priceRuleAdminService.restorePriceRule(priceRuleId)));
    }
}
