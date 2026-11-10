package com.cinema.modules.fnb.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.fnb.dto.request.FnbItemRequest;
import com.cinema.modules.fnb.dto.response.FnbItemResponse;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import com.cinema.modules.fnb.service.interfaces.IFnbItemAdminService;
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
@RequestMapping("/api/v1/admin/fnb/items")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminFnbItemController {

    private final IFnbItemAdminService fnbItemAdminService;

    public AdminFnbItemController(IFnbItemAdminService fnbItemAdminService) {
        this.fnbItemAdminService = fnbItemAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<ApiResponse<PageResponse<FnbItemResponse>>> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) FnbCategory category,
            @RequestParam(required = false) FnbStatus status,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbItemAdminService.searchItems(keyword, category, status, deleted, page, size)));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<FnbItemResponse>>> listActiveItems() {
        return ResponseEntity.ok(ApiResponse.success(fnbItemAdminService.listActiveItems()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FnbItemResponse>> createItem(@Valid @RequestBody FnbItemRequest fnbItemRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm món thành công", fnbItemAdminService.createItem(fnbItemRequest)));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ApiResponse<FnbItemResponse>> updateItem(
            @PathVariable Long itemId, @Valid @RequestBody FnbItemRequest fnbItemRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật món thành công", fnbItemAdminService.updateItem(itemId, fnbItemRequest)));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Long itemId) {
        fnbItemAdminService.deleteItem(itemId);
        return ResponseEntity.ok(ApiResponse.success("Xóa món thành công", null));
    }

    @PostMapping("/{itemId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FnbItemResponse>> restoreItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("Khôi phục món thành công", fnbItemAdminService.restoreItem(itemId)));
    }
}
