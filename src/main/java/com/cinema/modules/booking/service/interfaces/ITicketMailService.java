package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.event.CounterReceiptEmailRequestedEvent;
import com.cinema.modules.booking.event.TicketReissuedEvent;

public interface ITicketMailService {

    void onBookingPaid(BookingPaidEvent bookingPaidEvent);

    void onTicketReissued(TicketReissuedEvent ticketReissuedEvent);

    void onCounterReceiptEmailRequested(CounterReceiptEmailRequestedEvent counterReceiptEmailRequestedEvent);
}
