package com.cinema.modules.staff.service;

import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.staff.dto.response.StaffBranchResponse;
import com.cinema.modules.staff.dto.response.StaffContextResponse;
import com.cinema.modules.staff.service.interfaces.IStaffContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class StaffContextService implements IStaffContextService {

    private final IBranchAccessService branchAccessService;
    private final ICinemaQueryService cinemaQueryService;

    public StaffContextService(IBranchAccessService branchAccessService, ICinemaQueryService cinemaQueryService) {
        this.branchAccessService = branchAccessService;
        this.cinemaQueryService = cinemaQueryService;
    }

    @Transactional(readOnly = true)
    @Override
    public StaffContextResponse getContext(Long userId) {
        boolean allBranches = branchAccessService.hasAccessToAllCinemas(userId);
        if (allBranches) {
            List<StaffBranchResponse> allStaffBranchResponses = cinemaQueryService.findActiveCinemaOptions().stream()
                    .map(cinemaOptionResponse -> new StaffBranchResponse(
                            cinemaOptionResponse.id(), cinemaOptionResponse.name(), false))
                    .toList();
            return new StaffContextResponse(true, allStaffBranchResponses);
        }
        Set<Long> activeCinemaIds =
                cinemaQueryService.findActiveCinemaIds(branchAccessService.getAccessibleCinemaIds(userId));
        List<StaffBranchResponse> assignedStaffBranchResponses = branchAccessService.getAssignedBranches(userId).stream()
                .filter(branchAssignmentResponse -> activeCinemaIds.contains(branchAssignmentResponse.cinemaId()))
                .map(branchAssignmentResponse -> new StaffBranchResponse(
                        branchAssignmentResponse.cinemaId(),
                        branchAssignmentResponse.cinemaName(),
                        branchAssignmentResponse.primary()))
                .toList();
        return new StaffContextResponse(false, assignedStaffBranchResponses);
    }
}
