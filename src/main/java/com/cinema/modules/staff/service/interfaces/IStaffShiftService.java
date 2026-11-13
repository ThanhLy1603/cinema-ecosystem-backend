package com.cinema.modules.staff.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.staff.dto.request.CloseShiftRequest;
import com.cinema.modules.staff.dto.request.OpenShiftRequest;
import com.cinema.modules.staff.dto.response.ShiftReportResponse;
import com.cinema.modules.staff.dto.response.ShiftResponse;

import java.time.Instant;
import java.util.List;

public interface IStaffShiftService {

    ShiftReportResponse openShift(Long staffUserId, OpenShiftRequest openShiftRequest);

    ShiftReportResponse findCurrentShift(Long staffUserId);

    ShiftReportResponse closeShift(Long staffUserId, CloseShiftRequest closeShiftRequest);

    List<ShiftResponse> findMyShifts(Long staffUserId, int limit);

    ShiftReportResponse findShiftReport(Long userId, Long shiftId);

    PageResponse<ShiftResponse> findShifts(
            Long managerUserId, Long cinemaId, Long staffUserId, Instant from, Instant to, int page, int size);
}
