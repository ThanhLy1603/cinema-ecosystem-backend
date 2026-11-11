package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.event.BookingPaidEvent;

public interface ITicketMailService {

    void onBookingPaid(BookingPaidEvent bookingPaidEvent);
}
