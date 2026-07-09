package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.ChargeResponse;
import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charges")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResponse create(@Valid @RequestBody CreateChargeRequest request) {
        return toResponse(chargeService.create(request));
    }

    @GetMapping("/{id}")
    public ChargeResponse getById(@PathVariable UUID id) {
        return toResponse(chargeService.getById(id));
    }

    @GetMapping("/invoice/{invoiceId}")
    public List<ChargeResponse> listByInvoice(@PathVariable UUID invoiceId) {
        return chargeService.listByInvoice(invoiceId).stream().map(this::toResponse).toList();
    }

    private ChargeResponse toResponse(Charge charge) {
        return new ChargeResponse(
                charge.getId(),
                charge.getInvoice().getId(),
                charge.getGateway(),
                charge.getGatewayPreferenceId(),
                charge.getGatewayPaymentId(),
                charge.getPaymentUrl(),
                charge.getStatus(),
                charge.getAmount()
        );
    }
}
