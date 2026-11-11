package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.request.BookingFnbRequest;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingFnbResponse;

import java.util.List;

public interface IBookingFnbService {

    BookingFnbResponse getPendingFnb(Long userId);

    BookingFnbResponse replacePendingFnb(Long userId, BookingFnbRequest bookingFnbRequest);

    List<BookingFnbLineResponse> findFnbLines(Long bookingId);
}
