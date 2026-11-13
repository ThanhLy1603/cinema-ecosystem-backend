package com.cinema.modules.promotion.listener;

import com.cinema.modules.booking.event.BookingRefundedEvent;
import com.cinema.modules.promotion.service.interfaces.ILoyaltyService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyBookingRefundedListener {

    private final ILoyaltyService loyaltyService;

    public LoyaltyBookingRefundedListener(ILoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @EventListener
    public void onBookingRefunded(BookingRefundedEvent bookingRefundedEvent) {
        if (bookingRefundedEvent.userId() == null) {
            return;
        }
        loyaltyService.revokePointsForBooking(bookingRefundedEvent.userId(), bookingRefundedEvent.bookingId());
    }
}
