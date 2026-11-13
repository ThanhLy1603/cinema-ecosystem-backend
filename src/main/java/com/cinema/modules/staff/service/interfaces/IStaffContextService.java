package com.cinema.modules.staff.service.interfaces;

import com.cinema.modules.staff.dto.response.StaffContextResponse;

public interface IStaffContextService {

    StaffContextResponse getContext(Long userId);
}
