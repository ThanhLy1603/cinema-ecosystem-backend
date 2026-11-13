package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.BookingExceptionResponse;

public interface IBookingExceptionService {

    BookingExceptionResponse lookup(Long managerUserId, String code);

    BookingExceptionResponse reissueTicket(Long managerUserId, String bookingCode, String reason);

    BookingExceptionResponse refundBooking(Long managerUserId, String bookingCode, String reason);
}
