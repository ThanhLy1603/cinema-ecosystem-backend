package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.request.TicketCheckInRequest;
import com.cinema.modules.booking.dto.request.TicketScanRequest;
import com.cinema.modules.booking.dto.request.TicketUndoRequest;
import com.cinema.modules.booking.dto.response.TicketScanLogResponse;
import com.cinema.modules.booking.dto.response.TicketScanResponse;

import java.util.List;

public interface ITicketCheckInService {

    TicketScanResponse scan(Long staffUserId, TicketScanRequest ticketScanRequest);

    TicketScanResponse checkIn(Long staffUserId, TicketCheckInRequest ticketCheckInRequest);

    TicketScanResponse undoCheckIn(Long staffUserId, TicketUndoRequest ticketUndoRequest);

    List<TicketScanLogResponse> findHistory(Long staffUserId, Long cinemaId);
}
