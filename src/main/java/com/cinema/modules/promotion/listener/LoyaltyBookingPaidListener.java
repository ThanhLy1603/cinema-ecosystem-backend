package com.cinema.modules.promotion.listener;

import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.promotion.service.interfaces.ILoyaltyService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyBookingPaidListener {

    private final ILoyaltyService loyaltyService;

    public LoyaltyBookingPaidListener(ILoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @EventListener
    public void onBookingPaid(BookingPaidEvent bookingPaidEvent) {
        if (bookingPaidEvent.userId() == null) {
            return;
        }
        loyaltyService.earnPointsForBooking(
                bookingPaidEvent.userId(), bookingPaidEvent.bookingId(), bookingPaidEvent.finalAmount());
    }
}
