package com.cinema.modules.payment.service;

import com.cinema.modules.payment.dto.response.RefundedPaymentResponse;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.IPaymentRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentRefundService implements IPaymentRefundService {

    private final PaymentRepository paymentRepository;

    @Transactional
    @Override
    public Optional<RefundedPaymentResponse> markBookingPaymentRefunded(Long bookingId) {
        return paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCESS)
                .map(payment -> {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    return new RefundedPaymentResponse(payment.getPaymentMethod(), payment.getAmount());
                });
    }
}
