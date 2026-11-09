package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.AssignBranchesRequest;
import com.cinema.modules.auth.dto.request.CreateStaffRequest;
import com.cinema.modules.auth.dto.request.UpdateStaffRequest;
import com.cinema.modules.auth.dto.response.StaffResponse;

public interface IStaffManagementService {

    StaffResponse createStaff(Long actorUserId, CreateStaffRequest createStaffRequest, String ipAddress);

    StaffResponse getStaff(Long userId);

    StaffResponse updateStaff(Long actorUserId, Long userId, UpdateStaffRequest updateStaffRequest, String ipAddress);

    StaffResponse assignBranches(Long actorUserId, Long userId, AssignBranchesRequest assignBranchesRequest, String ipAddress);
}
