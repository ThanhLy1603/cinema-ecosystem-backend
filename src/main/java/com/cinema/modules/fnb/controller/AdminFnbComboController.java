package com.cinema.modules.fnb.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbComboRequest;
import com.cinema.modules.fnb.dto.response.FnbComboResponse;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.service.interfaces.IFnbComboAdminService;
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

@RestController
@RequestMapping("/api/v1/admin/fnb/combos")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminFnbComboController {

    private final IFnbComboAdminService fnbComboAdminService;

    public AdminFnbComboController(IFnbComboAdminService fnbComboAdminService) {
        this.fnbComboAdminService = fnbComboAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<ApiResponse<PageResponse<FnbComboResponse>>> searchCombos(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) FnbStatus status,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbComboAdminService.searchCombos(keyword, status, deleted, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FnbComboResponse>> createCombo(
            @Valid @RequestBody FnbComboRequest fnbComboRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm combo thành công", fnbComboAdminService.createCombo(fnbComboRequest)));
    }

    @PutMapping("/{comboId}")
    public ResponseEntity<ApiResponse<FnbComboResponse>> updateCombo(
            @PathVariable Long comboId, @Valid @RequestBody FnbComboRequest fnbComboRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật combo thành công", fnbComboAdminService.updateCombo(comboId, fnbComboRequest)));
    }

    @DeleteMapping("/{comboId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCombo(@PathVariable Long comboId) {
        fnbComboAdminService.deleteCombo(comboId);
        return ResponseEntity.ok(ApiResponse.success("Xóa combo thành công", null));
    }

    @PostMapping("/{comboId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FnbComboResponse>> restoreCombo(@PathVariable Long comboId) {
        return ResponseEntity.ok(
                ApiResponse.success("Khôi phục combo thành công", fnbComboAdminService.restoreCombo(comboId)));
    }
}
