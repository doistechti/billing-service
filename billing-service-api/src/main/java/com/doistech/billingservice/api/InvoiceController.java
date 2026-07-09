package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.CreateInvoiceRequest;
import com.doistech.billingservice.api.BillingApi.InvoiceItemResponse;
import com.doistech.billingservice.api.BillingApi.InvoiceResponse;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return toResponse(invoiceService.create(request));
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return toResponse(invoiceService.getById(id));
    }

    @GetMapping("/by-reference")
    public InvoiceResponse getByReference(
            @RequestParam String sourceSystem,
            @RequestParam String externalReference
    ) {
        return toResponse(invoiceService.getByReference(sourceSystem, externalReference));
    }

    @PatchMapping("/{id}/cancel")
    public InvoiceResponse cancel(@PathVariable UUID id) {
        return toResponse(invoiceService.cancel(id));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomer().getId(),
                invoice.getSourceSystem(),
                invoice.getExternalReference(),
                invoice.getDescription(),
                invoice.getTotalAmount(),
                invoice.getDueDate(),
                invoice.getPaidAt(),
                invoice.getCanceledAt(),
                invoice.getStatus(),
                invoice.getItems().stream()
                        .map(item -> new InvoiceItemResponse(
                                item.getId(),
                                item.getDescription(),
                                item.getQuantity(),
                                item.getUnitAmount(),
                                item.getTotalAmount()))
                        .toList()
        );
    }
}
