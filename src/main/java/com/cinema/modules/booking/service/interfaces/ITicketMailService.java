package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.event.CounterReceiptEmailRequestedEvent;

public interface ITicketMailService {

    void onBookingPaid(BookingPaidEvent bookingPaidEvent);

    void onCounterReceiptEmailRequested(CounterReceiptEmailRequestedEvent counterReceiptEmailRequestedEvent);
}
