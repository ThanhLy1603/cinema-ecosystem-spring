package com.cinema.modules.promotion.listener;

import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.promotion.service.interfaces.ILoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoyaltyBookingPaidListener {

    private final ILoyaltyService loyaltyService;

    @EventListener
    public void onBookingPaid(BookingPaidEvent bookingPaidEvent) {
        if (bookingPaidEvent.userId() == null) {
            return;
        }
        loyaltyService.earnPointsForBooking(
                bookingPaidEvent.userId(), bookingPaidEvent.bookingId(), bookingPaidEvent.finalAmount());
    }
}
