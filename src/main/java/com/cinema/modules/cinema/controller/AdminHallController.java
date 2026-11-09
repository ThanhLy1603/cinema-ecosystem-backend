package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.request.HallRequest;
import com.cinema.modules.cinema.dto.response.HallResponse;
import com.cinema.modules.cinema.service.interfaces.IHallService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminHallController {

    private final IHallService hallService;

    public AdminHallController(IHallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping("/cinemas/{cinemaId}/halls")
    public ResponseEntity<ApiResponse<List<HallResponse>>> listHalls(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long cinemaId,
            @RequestParam(defaultValue = "false") boolean deleted) {
        return ResponseEntity.ok(
                ApiResponse.success(hallService.listHalls(authenticatedUser.userId(), cinemaId, deleted)));
    }

    @PostMapping("/cinemas/{cinemaId}/halls")
    public ResponseEntity<ApiResponse<HallResponse>> createHall(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long cinemaId,
            @Valid @RequestBody HallRequest hallRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Thêm phòng chiếu thành công", hallService.createHall(authenticatedUser.userId(), cinemaId, hallRequest)));
    }

    @PutMapping("/halls/{hallId}")
    public ResponseEntity<ApiResponse<HallResponse>> updateHall(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long hallId,
            @Valid @RequestBody HallRequest hallRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật phòng chiếu thành công", hallService.updateHall(authenticatedUser.userId(), hallId, hallRequest)));
    }

    @DeleteMapping("/halls/{hallId}")
    public ResponseEntity<ApiResponse<Void>> deleteHall(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long hallId) {
        hallService.deleteHall(authenticatedUser.userId(), hallId);
        return ResponseEntity.ok(ApiResponse.success("Xóa phòng chiếu thành công", null));
    }

    @PostMapping("/halls/{hallId}/restore")
    public ResponseEntity<ApiResponse<HallResponse>> restoreHall(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long hallId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Khôi phục phòng chiếu thành công", hallService.restoreHall(authenticatedUser.userId(), hallId)));
    }
}
