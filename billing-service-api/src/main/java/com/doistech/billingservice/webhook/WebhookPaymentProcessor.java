package com.doistech.billingservice.webhook;

import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.charge.ChargeRepository;
import com.doistech.billingservice.core.charge.ChargeStatusSynchronizer;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.PaymentGatewayRouter;
import com.doistech.billingservice.shared.BusinessRuleException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Consulta o pagamento no gateway (fonte da verdade) e aplica o resultado na charge correspondente.
 */
@Component
public class WebhookPaymentProcessor {

    private final MerchantService merchantService;
    private final PaymentGatewayRouter gatewayRouter;
    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final ChargeStatusSynchronizer synchronizer;

    public WebhookPaymentProcessor(
            MerchantService merchantService,
            PaymentGatewayRouter gatewayRouter,
            ChargeRepository chargeRepository,
            InvoiceRepository invoiceRepository,
            ChargeStatusSynchronizer synchronizer
    ) {
        this.merchantService = merchantService;
        this.gatewayRouter = gatewayRouter;
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.synchronizer = synchronizer;
    }

    @Transactional
    public Charge process(UUID merchantId, String gatewayPaymentId) {
        MerchantCredentials credentials = merchantService.credentials(merchantId);
        Gateway gateway = merchantService.gatewayOf(merchantId);
        GatewayPaymentResponse payment = gatewayRouter.forGateway(gateway).getPayment(credentials, gatewayPaymentId);
        Charge charge = chargeRepository
                .findFirstByMerchantIdAndGatewayAndGatewayPaymentId(merchantId, gateway, payment.gatewayPaymentId())
                .orElseGet(() -> resolveByExternalReference(merchantId, payment));
        synchronizer.apply(charge, payment);
        return charge;
    }

    private Charge resolveByExternalReference(UUID merchantId, GatewayPaymentResponse payment) {
        UUID invoiceId = parseInvoiceId(payment.externalReference());
        if (invoiceId == null) {
            throw new BusinessRuleException("invoice id not found in external reference");
        }
        Invoice invoice = invoiceRepository.findByIdAndMerchantId(invoiceId, merchantId)
                .orElseThrow(() -> new BusinessRuleException("invoice not found"));
        return chargeRepository.findFirstByInvoiceIdAndMethodOrderByCreatedAtDesc(invoice.getId(), ChargeMethod.CHECKOUT_PRO)
                .or(() -> chargeRepository.findFirstByInvoiceIdOrderByCreatedAtDesc(invoice.getId()))
                .orElseThrow(() -> new BusinessRuleException("charge not found for invoice"));
    }

    static UUID parseInvoiceId(String externalReference) {
        if (!StringUtils.hasText(externalReference)) {
            return null;
        }
        for (String segment : externalReference.split("\\|")) {
            if (segment.startsWith("INVOICE:")) {
                try {
                    return UUID.fromString(segment.substring("INVOICE:".length()));
                } catch (IllegalArgumentException exception) {
                    return null;
                }
            }
        }
        return null;
    }
}
