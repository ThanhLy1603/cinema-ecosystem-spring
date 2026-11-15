package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.properties.ReceiptProperties;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.dto.response.CounterReceiptFileResponse;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.ICounterFnbSaleService;
import com.cinema.modules.payment.service.interfaces.ICounterReceiptFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CounterReceiptFileService implements ICounterReceiptFileService {

    static final String RECEIPT_FILE_EXTENSION = ".pdf";

    private final IBookingDetailService bookingDetailService;
    private final IBranchAccessService branchAccessService;
    private final PaymentRepository paymentRepository;
    private final CounterReceiptPdfRenderer counterReceiptPdfRenderer;
    private final ICounterFnbSaleService counterFnbSaleService;
    private final ReceiptProperties receiptProperties;

    @Transactional(readOnly = true)
    @Override
    public CounterReceiptFileResponse saveReceiptFile(Long staffUserId, String bookingCode) {
        BookingDetailResponse bookingDetailResponse = bookingDetailService.findBookingDetailByCode(bookingCode)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        branchAccessService.assertCanAccessCinema(staffUserId, bookingDetailResponse.showtime().cinemaId());
        Payment payment = paymentRepository
                .findByTransactionRef(bookingCode + CounterPaymentService.TRANSACTION_REF_SUFFIX)
                .orElseThrow(() -> new AppException(
                        "Đơn này không phải đơn bán tại quầy", HttpStatus.NOT_FOUND, "COUNTER_PAYMENT_NOT_FOUND"));
        Map<String, String> responseData = payment.getResponseData() == null ? Map.of() : payment.getResponseData();
        CounterReceiptResponse counterReceiptResponse = new CounterReceiptResponse(
                bookingDetailResponse,
                payment.getPaymentMethod(),
                parseAmount(responseData.get("cashReceived")),
                parseAmount(responseData.get("changeAmount")),
                responseData.get("staffName"),
                responseData.get("customerName"),
                payment.getPaidAt());
        return writeReceiptFile(bookingCode, counterReceiptPdfRenderer.render(counterReceiptResponse));
    }

    @Transactional(readOnly = true)
    @Override
    public CounterReceiptFileResponse saveFnbReceiptFile(Long staffUserId, String orderCode) {
        CounterFnbReceiptResponse counterFnbReceiptResponse = counterFnbSaleService.findReceipt(staffUserId, orderCode);
        return writeReceiptFile(orderCode, counterReceiptPdfRenderer.renderFnbReceipt(counterFnbReceiptResponse));
    }

    private CounterReceiptFileResponse writeReceiptFile(String code, byte[] receiptBytes) {
        Path receiptDirectory = Path.of(receiptProperties.directory()).toAbsolutePath().normalize();
        String fileName = code + RECEIPT_FILE_EXTENSION;
        try {
            Files.createDirectories(receiptDirectory);
            Files.write(receiptDirectory.resolve(fileName), receiptBytes);
        } catch (IOException ioException) {
            throw new AppException(
                    "Không lưu được hóa đơn vào thư mục " + receiptDirectory,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RECEIPT_SAVE_FAILED");
        }
        return new CounterReceiptFileResponse(fileName, receiptDirectory.toString());
    }

    private BigDecimal parseAmount(String amount) {
        return amount == null ? null : new BigDecimal(amount);
    }
}
