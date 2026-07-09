package com.doistech.billingservice.api;

import com.doistech.billingservice.core.charge.ChargeStatus;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingApi {

    private BillingApi() {
    }

    public record CreateCustomerRequest(
            @NotBlank(message = "sourceSystem is required") String sourceSystem,
            @NotBlank(message = "externalReference is required") String externalReference,
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
            String document,
            String phone
    ) {
    }

    public record CustomerResponse(
            UUID id,
            String sourceSystem,
            String externalReference,
            String name,
            String email,
            boolean active
    ) {
    }

    public record CreateInvoiceRequest(
            @NotNull(message = "customerId is required") UUID customerId,
            @NotBlank(message = "sourceSystem is required") String sourceSystem,
            @NotBlank(message = "externalReference is required") String externalReference,
            @NotBlank(message = "description is required") String description,
            LocalDate dueDate,
            @NotEmpty(message = "items are required") List<@Valid CreateInvoiceItemRequest> items
    ) {
    }

    public record CreateInvoiceItemRequest(
            @NotBlank(message = "item description is required") String description,
            @NotNull(message = "item quantity is required") @Positive(message = "item quantity must be positive") BigDecimal quantity,
            @NotNull(message = "item unitAmount is required") @Positive(message = "item unitAmount must be positive") BigDecimal unitAmount
    ) {
    }

    public record InvoiceItemResponse(
            UUID id,
            String description,
            BigDecimal quantity,
            BigDecimal unitAmount,
            BigDecimal totalAmount
    ) {
    }

    public record InvoiceResponse(
            UUID id,
            UUID customerId,
            String sourceSystem,
            String externalReference,
            String description,
            BigDecimal totalAmount,
            LocalDate dueDate,
            LocalDateTime paidAt,
            LocalDateTime canceledAt,
            InvoiceStatus status,
            List<InvoiceItemResponse> items
    ) {
    }

    public record CreateChargeRequest(
            @NotNull(message = "invoiceId is required") UUID invoiceId,
            @NotNull(message = "gateway is required") Gateway gateway
    ) {
    }

    public record ChargeResponse(
            UUID id,
            UUID invoiceId,
            Gateway gateway,
            String gatewayPreferenceId,
            String gatewayPaymentId,
            String paymentUrl,
            ChargeStatus status,
            BigDecimal amount
    ) {
    }

    public record PaymentResponse(
            UUID id,
            UUID invoiceId,
            UUID chargeId,
            Gateway gateway,
            String gatewayPaymentId,
            String gatewayStatus,
            BigDecimal amount,
            LocalDateTime paidAt
    ) {
    }

    public record HealthResponse(String status, String service) {
    }

    public record WebhookReceivedResponse(String status) {
    }
}
