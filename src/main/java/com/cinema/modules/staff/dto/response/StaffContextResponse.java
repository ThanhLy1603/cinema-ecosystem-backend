package com.cinema.modules.staff.dto.response;

import java.util.List;

public record StaffContextResponse(boolean allBranches, List<StaffBranchResponse> branches) {
}
