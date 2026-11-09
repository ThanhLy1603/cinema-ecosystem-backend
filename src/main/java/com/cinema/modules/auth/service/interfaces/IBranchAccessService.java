package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.response.BranchAssignmentResponse;

import java.util.List;
import java.util.Set;

public interface IBranchAccessService {

    Set<Long> getAccessibleCinemaIds(Long userId);

    boolean hasAccessToAllCinemas(Long userId);

    boolean canAccessCinema(Long userId, Long cinemaId);

    void assertCanAccessCinema(Long userId, Long cinemaId);

    List<BranchAssignmentResponse> getAssignedBranches(Long userId);

    void evict(Long userId);

    void evictAfterCommit(Long userId);
}
