package com.cinema.modules.promotion.listener;

import com.cinema.modules.booking.event.BookingRefundedEvent;
import com.cinema.modules.promotion.service.interfaces.ILoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoyaltyBookingRefundedListener {

    private final ILoyaltyService loyaltyService;

    @EventListener
    public void onBookingRefunded(BookingRefundedEvent bookingRefundedEvent) {
        if (bookingRefundedEvent.userId() == null) {
            return;
        }
        loyaltyService.revokePointsForBooking(bookingRefundedEvent.userId(), bookingRefundedEvent.bookingId());
    }
}
