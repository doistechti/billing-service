package com.doistech.billingservice.core.charge;

import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.mercadopago.ChargeGatewayResponse;
import com.doistech.billingservice.gateway.mercadopago.PaymentGatewayService;
import com.doistech.billingservice.shared.BusinessRuleException;
import com.doistech.billingservice.shared.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewayService paymentGatewayService;

    public ChargeService(
            ChargeRepository chargeRepository,
            InvoiceRepository invoiceRepository,
            PaymentGatewayService paymentGatewayService
    ) {
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentGatewayService = paymentGatewayService;
    }

    @Transactional
    public Charge create(CreateChargeRequest request) {
        log.info("creating charge request invoiceId={} gateway={}", request.invoiceId(), request.gateway());
        if (request.gateway() != Gateway.MERCADO_PAGO) {
            throw new BusinessRuleException("unsupported payment gateway");
        }

        Invoice invoice = invoiceRepository.findDetailedById(request.invoiceId())
                .orElseThrow(() -> new NotFoundException("invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("paid invoice cannot generate a new charge");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            throw new BusinessRuleException("canceled invoice cannot generate a charge");
        }
        if (chargeRepository.existsByInvoiceIdAndStatusIn(
                invoice.getId(),
                List.of(ChargeStatus.CREATED, ChargeStatus.WAITING_PAYMENT))) {
            throw new BusinessRuleException("invoice already has an active charge");
        }

        ChargeGatewayResponse gatewayResponse = paymentGatewayService.createCharge(invoice);

        Charge charge = new Charge();
        charge.setInvoice(invoice);
        charge.setGateway(gatewayResponse.gateway());
        charge.setGatewayPreferenceId(gatewayResponse.gatewayPreferenceId());
        charge.setPaymentUrl(gatewayResponse.paymentUrl());
        charge.setAmount(gatewayResponse.amount());
        charge.setStatus(gatewayResponse.status());

        invoice.setStatus(InvoiceStatus.WAITING_PAYMENT);
        invoiceRepository.save(invoice);
        Charge savedCharge = chargeRepository.save(charge);
        log.info(
                "charge created successfully chargeId={} invoiceId={} gateway={} preferenceId={} status={}",
                savedCharge.getId(),
                invoice.getId(),
                savedCharge.getGateway(),
                savedCharge.getGatewayPreferenceId(),
                savedCharge.getStatus());
        return savedCharge;
    }

    @Transactional(readOnly = true)
    public Charge getById(UUID id) {
        return chargeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("charge not found"));
    }

    @Transactional(readOnly = true)
    public List<Charge> listByInvoice(UUID invoiceId) {
        return chargeRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }
}
