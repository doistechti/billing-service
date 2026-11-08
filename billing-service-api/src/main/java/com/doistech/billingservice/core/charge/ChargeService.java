package com.doistech.billingservice.core.charge;

import com.doistech.billingservice.api.BillingApi.AddressRequest;
import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.api.BillingApi.PayerRequest;
import com.doistech.billingservice.core.customer.Customer;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceRepository;
import com.doistech.billingservice.core.invoice.InvoiceStatus;
import com.doistech.billingservice.core.merchant.MerchantCredentials;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.gateway.ChargeGatewayResponse;
import com.doistech.billingservice.gateway.DirectPaymentRequest;
import com.doistech.billingservice.gateway.GatewayPaymentResponse;
import com.doistech.billingservice.gateway.GatewayRefundResponse;
import com.doistech.billingservice.gateway.mercadopago.MercadoPagoGatewayService;
import com.doistech.billingservice.gateway.PaymentGatewayService;
import com.doistech.billingservice.shared.BusinessRuleException;
import com.doistech.billingservice.shared.ConflictException;
import com.doistech.billingservice.shared.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final int DEFAULT_PIX_MINUTES = 30;
    private static final int DEFAULT_BOLETO_MINUTES = 3 * 24 * 60;

    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final MerchantService merchantService;
    private final ChargeStatusSynchronizer synchronizer;

    public ChargeService(
            ChargeRepository chargeRepository,
            InvoiceRepository invoiceRepository,
            PaymentGatewayService paymentGatewayService,
            MerchantService merchantService,
            ChargeStatusSynchronizer synchronizer
    ) {
        this.chargeRepository = chargeRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.merchantService = merchantService;
        this.synchronizer = synchronizer;
    }

    @Transactional
    public Charge create(UUID merchantId, CreateChargeRequest request, String idempotencyKey) {
        ChargeMethod method = request.methodOrDefault();
        log.info("creating charge merchantId={} invoiceId={} gateway={} method={} idempotencyKeyPresent={}",
                merchantId, request.invoiceId(), request.gateway(), method, StringUtils.hasText(idempotencyKey));

        if (StringUtils.hasText(idempotencyKey)) {
            Optional<Charge> existing = chargeRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getInvoice().getId().equals(request.invoiceId())) {
                    throw new ConflictException("idempotency key already used for another invoice");
                }
                log.info("idempotent replay chargeId={} idempotencyKey={}", existing.get().getId(), idempotencyKey);
                return existing.get();
            }
        }

        if (request.gateway() != Gateway.MERCADO_PAGO) {
            throw new BusinessRuleException("unsupported payment gateway");
        }

        Invoice invoice = invoiceRepository.findByIdAndMerchantId(request.invoiceId(), merchantId)
                .orElseThrow(() -> new NotFoundException("invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new BusinessRuleException("paid invoice cannot generate a new charge");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            throw new BusinessRuleException("canceled invoice cannot generate a charge");
        }
        if (chargeRepository.existsByInvoiceIdAndStatusIn(invoice.getId(), ChargeStatus.ACTIVE)) {
            throw new BusinessRuleException("invoice already has an active charge");
        }

        MerchantCredentials credentials = merchantService.credentials(merchantId);

        Charge charge = new Charge();
        charge.setMerchantId(merchantId);
        charge.setInvoice(invoice);
        charge.setGateway(Gateway.MERCADO_PAGO);
        charge.setMethod(method);
        charge.setAmount(invoice.getTotalAmount());
        charge.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);

        if (method == ChargeMethod.CHECKOUT_PRO) {
            ChargeGatewayResponse gatewayResponse = paymentGatewayService.createCharge(credentials, invoice);
            charge.setGatewayPreferenceId(gatewayResponse.gatewayPreferenceId());
            charge.setPaymentUrl(gatewayResponse.paymentUrl());
            charge.setStatus(gatewayResponse.status());
            invoice.setStatus(InvoiceStatus.WAITING_PAYMENT);
            invoiceRepository.save(invoice);
            Charge saved = chargeRepository.save(charge);
            log.info("checkout pro charge created chargeId={} invoiceId={} preferenceId={}",
                    saved.getId(), invoice.getId(), saved.getGatewayPreferenceId());
            return saved;
        }

        DirectPaymentRequest paymentRequest = toDirectPayment(invoice, request, method, idempotencyKey);
        GatewayPaymentResponse payment = paymentGatewayService.createPayment(credentials, paymentRequest);

        // A charge nasce como WAITING_PAYMENT; o sincronizador aplica o status real (cartão pode vir aprovado).
        charge.setStatus(ChargeStatus.WAITING_PAYMENT);
        invoice.setStatus(InvoiceStatus.WAITING_PAYMENT);
        invoiceRepository.save(invoice);
        Charge saved = chargeRepository.save(charge);
        synchronizer.apply(saved, payment);
        log.info("direct charge created chargeId={} invoiceId={} method={} status={} paymentId={}",
                saved.getId(), invoice.getId(), method, saved.getStatus(), saved.getGatewayPaymentId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Charge getById(UUID merchantId, UUID id) {
        return chargeRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotFoundException("charge not found"));
    }

    @Transactional(readOnly = true)
    public List<Charge> listByInvoice(UUID merchantId, UUID invoiceId) {
        return chargeRepository.findByInvoiceIdAndMerchantIdOrderByCreatedAtDesc(invoiceId, merchantId);
    }

    /**
     * Consulta o gateway e aplica o status atual. Útil quando o webhook atrasou.
     */
    @Transactional
    public Charge sync(UUID merchantId, UUID id) {
        Charge charge = getById(merchantId, id);
        if (!StringUtils.hasText(charge.getGatewayPaymentId())) {
            return charge;
        }
        MerchantCredentials credentials = merchantService.credentials(merchantId);
        synchronizer.apply(charge, paymentGatewayService.getPayment(credentials, charge.getGatewayPaymentId()));
        return charge;
    }

    @Transactional
    public Charge cancel(UUID merchantId, UUID id) {
        Charge charge = getById(merchantId, id);
        if (!charge.getStatus().isActive()) {
            throw new BusinessRuleException("only pending charges can be canceled");
        }
        if (charge.getMethod() == ChargeMethod.CREDIT_CARD) {
            throw new BusinessRuleException("credit card charges cannot be canceled, refund instead");
        }
        if (StringUtils.hasText(charge.getGatewayPaymentId())) {
            MerchantCredentials credentials = merchantService.credentials(merchantId);
            synchronizer.apply(charge, paymentGatewayService.cancelPayment(credentials, charge.getGatewayPaymentId()));
        } else {
            synchronizer.cancelLocally(charge);
        }
        return charge;
    }

    @Transactional
    public Charge refund(UUID merchantId, UUID id, BigDecimal amount, String idempotencyKey) {
        Charge charge = getById(merchantId, id);
        if (charge.getStatus() != ChargeStatus.PAID) {
            throw new BusinessRuleException("only paid charges can be refunded");
        }
        if (!StringUtils.hasText(charge.getGatewayPaymentId())) {
            throw new BusinessRuleException("charge has no gateway payment to refund");
        }
        BigDecimal remaining = charge.getAmount().subtract(charge.getRefundedAmount());
        BigDecimal refundAmount = amount == null ? remaining : amount;
        if (refundAmount.compareTo(remaining) > 0) {
            throw new BusinessRuleException("refund amount exceeds the refundable balance");
        }

        MerchantCredentials credentials = merchantService.credentials(merchantId);
        // Estorno total vai sem valor para o gateway, que devolve o saldo inteiro.
        BigDecimal gatewayAmount = amount == null ? null : refundAmount;
        GatewayRefundResponse refund = paymentGatewayService.refund(
                credentials, charge.getGatewayPaymentId(), gatewayAmount, idempotencyKey);
        synchronizer.applyRefund(charge, refund.amount() == null ? refundAmount : refund.amount());
        log.info("charge refunded chargeId={} amount={} refundId={} status={}",
                charge.getId(), refundAmount, refund.refundId(), charge.getStatus());
        return charge;
    }

    private DirectPaymentRequest toDirectPayment(
            Invoice invoice, CreateChargeRequest request, ChargeMethod method, String idempotencyKey) {
        Customer customer = invoice.getCustomer();
        PayerRequest payer = request.payer();

        String name = firstText(payer == null ? null : payer.name(), customer.getName());
        String email = firstText(payer == null ? null : payer.email(), customer.getEmail());
        String document = onlyDigits(firstText(payer == null ? null : payer.document(), customer.getDocument()));
        if (document == null || !(document.length() == 11 || document.length() == 14)) {
            throw new BusinessRuleException("payer document (CPF or CNPJ) is required for " + method);
        }

        AddressRequest address = payer == null ? null : payer.address();
        if (method == ChargeMethod.BOLETO && address == null) {
            throw new BusinessRuleException("payer address is required for BOLETO");
        }
        if (method == ChargeMethod.CREDIT_CARD && request.card() == null) {
            throw new BusinessRuleException("card is required for CREDIT_CARD");
        }

        String[] nameParts = splitName(name);
        DirectPaymentRequest.Address mpAddress = address == null ? null : new DirectPaymentRequest.Address(
                address.zipCode(), address.street(), address.number(), address.neighborhood(), address.city(), address.state());
        DirectPaymentRequest.Card card = request.card() == null ? null : new DirectPaymentRequest.Card(
                request.card().token(),
                request.card().paymentMethodId(),
                request.card().issuerId(),
                request.card().installments());

        return new DirectPaymentRequest(
                method,
                invoice.getTotalAmount(),
                invoice.getDescription(),
                MercadoPagoGatewayService.buildExternalReference(invoice),
                StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString(),
                expiration(method, request.expiresInMinutes()),
                new DirectPaymentRequest.Payer(
                        email,
                        nameParts[0],
                        nameParts[1],
                        document.length() == 11 ? "CPF" : "CNPJ",
                        document,
                        mpAddress),
                card);
    }

    private OffsetDateTime expiration(ChargeMethod method, Integer expiresInMinutes) {
        if (method == ChargeMethod.CREDIT_CARD) {
            return null;
        }
        int minutes = expiresInMinutes != null
                ? expiresInMinutes
                : method == ChargeMethod.PIX ? DEFAULT_PIX_MINUTES : DEFAULT_BOLETO_MINUTES;
        return OffsetDateTime.now(BRAZIL).plusMinutes(minutes);
    }

    private String[] splitName(String name) {
        if (!StringUtils.hasText(name)) {
            return new String[] {null, null};
        }
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space < 0
                ? new String[] {trimmed, null}
                : new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private String onlyDigits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
