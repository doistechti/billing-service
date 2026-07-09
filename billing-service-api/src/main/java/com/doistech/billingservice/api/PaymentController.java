package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.PaymentResponse;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    public PaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return toResponse(paymentQueryService.getById(id));
    }

    @GetMapping("/invoice/{invoiceId}")
    public List<PaymentResponse> listByInvoice(@PathVariable UUID invoiceId) {
        return paymentQueryService.listByInvoice(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getCharge().getId(),
                payment.getGateway(),
                payment.getGatewayPaymentId(),
                payment.getGatewayStatus(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}
