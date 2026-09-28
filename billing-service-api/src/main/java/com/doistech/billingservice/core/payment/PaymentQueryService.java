package com.doistech.billingservice.core.payment;

import com.doistech.billingservice.shared.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Payment getById(UUID merchantId, UUID id) {
        return paymentRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotFoundException("payment not found"));
    }

    @Transactional(readOnly = true)
    public List<Payment> listByInvoice(UUID merchantId, UUID invoiceId) {
        return paymentRepository.findByInvoiceIdAndMerchantIdOrderByCreatedAtDesc(invoiceId, merchantId);
    }
}
