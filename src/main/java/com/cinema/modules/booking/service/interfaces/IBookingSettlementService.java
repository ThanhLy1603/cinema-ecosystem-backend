package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;

import java.util.Optional;

public interface IBookingSettlementService {

    PayableBookingResponse requirePayableBooking(Long userId);

    SettlementOutcome settlePaidBooking(Long bookingId);

    SettlementOutcome settleCounterBooking(Long bookingId, Long customerUserId, Long staffUserId);

    Optional<BookingStatus> findOwnedBookingStatus(Long bookingId, Long userId);
}
