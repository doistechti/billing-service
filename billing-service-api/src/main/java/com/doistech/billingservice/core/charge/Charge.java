package com.doistech.billingservice.core.charge;

import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "charges")
public class Charge extends BaseEntity {

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Gateway gateway;

    @Column(name = "gateway_preference_id")
    private String gatewayPreferenceId;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "payment_url")
    private String paymentUrl;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChargeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChargeMethod method = ChargeMethod.CHECKOUT_PRO;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "status_detail")
    private String statusDetail;

    @Column
    private Integer installments;

    @Column(name = "pix_qr_code")
    private String pixQrCode;

    @Column(name = "pix_qr_code_base64")
    private String pixQrCodeBase64;

    @Column(name = "ticket_url")
    private String ticketUrl;

    @Column(name = "boleto_barcode")
    private String boletoBarcode;

    @Column(name = "boleto_digitable_line")
    private String boletoDigitableLine;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public String getGatewayPreferenceId() {
        return gatewayPreferenceId;
    }

    public void setGatewayPreferenceId(String gatewayPreferenceId) {
        this.gatewayPreferenceId = gatewayPreferenceId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public void setStatus(ChargeStatus status) {
        this.status = status;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public ChargeMethod getMethod() {
        return method;
    }

    public void setMethod(ChargeMethod method) {
        this.method = method;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public Integer getInstallments() {
        return installments;
    }

    public void setInstallments(Integer installments) {
        this.installments = installments;
    }

    public String getPixQrCode() {
        return pixQrCode;
    }

    public void setPixQrCode(String pixQrCode) {
        this.pixQrCode = pixQrCode;
    }

    public String getPixQrCodeBase64() {
        return pixQrCodeBase64;
    }

    public void setPixQrCodeBase64(String pixQrCodeBase64) {
        this.pixQrCodeBase64 = pixQrCodeBase64;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public void setTicketUrl(String ticketUrl) {
        this.ticketUrl = ticketUrl;
    }

    public String getBoletoBarcode() {
        return boletoBarcode;
    }

    public void setBoletoBarcode(String boletoBarcode) {
        this.boletoBarcode = boletoBarcode;
    }

    public String getBoletoDigitableLine() {
        return boletoDigitableLine;
    }

    public void setBoletoDigitableLine(String boletoDigitableLine) {
        this.boletoDigitableLine = boletoDigitableLine;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }
}
