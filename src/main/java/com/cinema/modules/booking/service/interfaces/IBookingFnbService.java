package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.request.BookingFnbRequest;
import com.cinema.modules.booking.dto.response.BookingFnbResponse;

public interface IBookingFnbService {

    BookingFnbResponse getPendingFnb(Long userId);

    BookingFnbResponse replacePendingFnb(Long userId, BookingFnbRequest bookingFnbRequest);
}
