package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.cinema.dto.request.FacilityRequest;
import com.cinema.modules.cinema.dto.response.FacilityResponse;
import com.cinema.modules.cinema.service.interfaces.IFacilityService;
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
@RequestMapping("/api/v1/admin/cinema-facilities")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminFacilityController {

    private final IFacilityService facilityService;

    public AdminFacilityController(IFacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<ApiResponse<List<FacilityResponse>>> listFacilities(
            @RequestParam(defaultValue = "false") boolean deleted) {
        return ResponseEntity.ok(ApiResponse.success(facilityService.listFacilities(deleted)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FacilityResponse>> createFacility(
            @Valid @RequestBody FacilityRequest facilityRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm tiện ích thành công", facilityService.createFacility(facilityRequest)));
    }

    @PutMapping("/{facilityId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FacilityResponse>> updateFacility(
            @PathVariable Long facilityId, @Valid @RequestBody FacilityRequest facilityRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật tiện ích thành công", facilityService.updateFacility(facilityId, facilityRequest)));
    }

    @DeleteMapping("/{facilityId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFacility(@PathVariable Long facilityId) {
        facilityService.deleteFacility(facilityId);
        return ResponseEntity.ok(ApiResponse.success("Xóa tiện ích thành công", null));
    }

    @PostMapping("/{facilityId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FacilityResponse>> restoreFacility(@PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success("Khôi phục tiện ích thành công", facilityService.restoreFacility(facilityId)));
    }
}
