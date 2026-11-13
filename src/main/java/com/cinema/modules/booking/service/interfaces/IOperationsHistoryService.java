package com.cinema.modules.booking.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.booking.dto.response.OperationsFnbDeliveryResponse;
import com.cinema.modules.booking.dto.response.OperationsScanLogResponse;
import com.cinema.modules.booking.enums.CheckInResultCode;

import java.time.Instant;

public interface IOperationsHistoryService {

    PageResponse<OperationsScanLogResponse> findScanLogs(
            Long managerUserId,
            Long cinemaId,
            Long staffUserId,
            CheckInResultCode checkInResultCode,
            Instant from,
            Instant to,
            int page,
            int size);

    PageResponse<OperationsFnbDeliveryResponse> findFnbDeliveries(
            Long managerUserId, Long cinemaId, Long staffUserId, Instant from, Instant to, int page, int size);
}
