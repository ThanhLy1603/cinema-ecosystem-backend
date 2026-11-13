package com.cinema.modules.staff.service.interfaces;

import com.cinema.modules.staff.dto.response.StaffOverviewResponse;

public interface IStaffOverviewService {

    StaffOverviewResponse findOverview(Long staffUserId, Long cinemaId);
}
