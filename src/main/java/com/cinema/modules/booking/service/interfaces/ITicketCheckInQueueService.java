package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.TicketQueueResponse;

public interface ITicketCheckInQueueService {

    TicketQueueResponse findQueue(Long staffUserId, Long cinemaId);
}
