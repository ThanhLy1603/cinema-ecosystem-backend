package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/cinema-options")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class CinemaOptionController {

    private final ICinemaQueryService cinemaQueryService;
    private final IBranchAccessService branchAccessService;

    public CinemaOptionController(ICinemaQueryService cinemaQueryService, IBranchAccessService branchAccessService) {
        this.cinemaQueryService = cinemaQueryService;
        this.branchAccessService = branchAccessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaOptionResponse>>> getCinemaOptions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        List<CinemaOptionResponse> cinemaOptionResponses = cinemaQueryService.findActiveCinemaOptions();
        if (branchAccessService.hasAccessToAllCinemas(authenticatedUser.userId())) {
            return ResponseEntity.ok(ApiResponse.success(cinemaOptionResponses));
        }
        Set<Long> accessibleCinemaIds = branchAccessService.getAccessibleCinemaIds(authenticatedUser.userId());
        return ResponseEntity.ok(ApiResponse.success(cinemaOptionResponses.stream()
                .filter(cinemaOptionResponse -> accessibleCinemaIds.contains(cinemaOptionResponse.id()))
                .toList()));
    }
}
