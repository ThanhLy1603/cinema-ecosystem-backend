package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.request.FnbPickupDeliverRequest;
import com.cinema.modules.booking.dto.request.FnbPickupScanRequest;
import com.cinema.modules.booking.dto.response.FnbDeliveryLogResponse;
import com.cinema.modules.booking.dto.response.FnbPickupQueueResponse;
import com.cinema.modules.booking.dto.response.FnbPickupScanResponse;

import java.util.List;

public interface IFnbPickupService {

    FnbPickupScanResponse scan(Long staffUserId, FnbPickupScanRequest fnbPickupScanRequest);

    FnbPickupScanResponse deliver(Long staffUserId, FnbPickupDeliverRequest fnbPickupDeliverRequest);

    FnbPickupQueueResponse findQueue(Long staffUserId, Long cinemaId);

    List<FnbDeliveryLogResponse> findHistory(Long staffUserId, Long cinemaId);
}
