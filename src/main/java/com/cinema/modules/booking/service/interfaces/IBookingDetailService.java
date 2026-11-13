package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.BookingDetailResponse;

import java.util.List;
import java.util.Optional;

public interface IBookingDetailService {

    Optional<BookingDetailResponse> findPendingBooking(Long userId);

    List<BookingDetailResponse> findMyBookings(Long userId);

    Optional<BookingDetailResponse> findBookingDetail(Long bookingId);

    Optional<BookingDetailResponse> findBookingDetailByCode(String bookingCode);
}
