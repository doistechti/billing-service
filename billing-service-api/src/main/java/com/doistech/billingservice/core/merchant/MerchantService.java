package com.doistech.billingservice.core.merchant;

import com.doistech.billingservice.api.MerchantApi.CreateMerchantRequest;
import com.doistech.billingservice.api.MerchantApi.UpdateMerchantRequest;
import com.doistech.billingservice.config.MercadoPagoProperties;
import com.doistech.billingservice.gateway.mercadopago.MercadoPagoAccount;
import com.doistech.billingservice.gateway.mercadopago.PaymentGatewayService;
import com.doistech.billingservice.shared.GatewayIntegrationException;
import com.doistech.billingservice.shared.BusinessRuleException;
import com.doistech.billingservice.shared.ConflictException;
import com.doistech.billingservice.shared.NotFoundException;
import com.doistech.billingservice.shared.crypto.Hmac;
import com.doistech.billingservice.shared.crypto.SecretCipher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MerchantService {

    private static final String API_KEY_PREFIX = "bk_";

    private final MerchantRepository merchantRepository;
    private final SecretCipher cipher;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final java.util.function.Function<String, MercadoPagoAccount> accountVerifier;

    public MerchantService(
            MerchantRepository merchantRepository,
            SecretCipher cipher,
            MercadoPagoProperties mercadoPagoProperties,
            @org.springframework.context.annotation.Lazy PaymentGatewayService paymentGatewayService
    ) {
        this.merchantRepository = merchantRepository;
        this.cipher = cipher;
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.accountVerifier = token -> {
            try {
                return paymentGatewayService.verifyAccount(token);
            } catch (GatewayIntegrationException exception) {
                if (exception.isTransient()) {
                    throw exception;
                }
                throw new BusinessRuleException("mercado pago rejected the access token");
            }
        };
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedMerchant> authenticate(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        return merchantRepository.findByApiKeyHash(Hmac.sha256Hex(apiKey))
                .filter(Merchant::isActive)
                .map(merchant -> new AuthenticatedMerchant(merchant.getId(), merchant.getCode()));
    }

    @Transactional
    public CreatedMerchant create(CreateMerchantRequest request) {
        if (merchantRepository.existsByCode(request.code())) {
            throw new ConflictException("merchant code already exists");
        }
        Merchant merchant = new Merchant();
        merchant.setCode(request.code());
        merchant.setName(request.name());
        merchant.setMercadoPagoAccessTokenEncrypted(encryptOrNull(request.mercadoPagoAccessToken()));
        merchant.setMercadoPagoPublicKey(blankToNull(request.mercadoPagoPublicKey()));
        merchant.setMercadoPagoWebhookSecretEncrypted(encryptOrNull(request.mercadoPagoWebhookSecret()));
        merchant.setSandbox(Boolean.TRUE.equals(request.sandbox()));
        merchant.setCallbackUrl(blankToNull(request.callbackUrl()));
        merchant.setActive(true);

        String apiKey = assignNewApiKey(merchant);
        String callbackSecret = assignNewCallbackSecret(merchant);
        return new CreatedMerchant(merchantRepository.save(merchant), apiKey, callbackSecret);
    }

    @Transactional
    public Merchant update(UUID id, UpdateMerchantRequest request) {
        Merchant merchant = getById(id);
        if (request.name() != null) {
            merchant.setName(request.name());
        }
        if (request.mercadoPagoAccessToken() != null) {
            merchant.setMercadoPagoAccessTokenEncrypted(encryptOrNull(request.mercadoPagoAccessToken()));
        }
        if (request.mercadoPagoPublicKey() != null) {
            merchant.setMercadoPagoPublicKey(blankToNull(request.mercadoPagoPublicKey()));
        }
        if (request.mercadoPagoWebhookSecret() != null) {
            merchant.setMercadoPagoWebhookSecretEncrypted(encryptOrNull(request.mercadoPagoWebhookSecret()));
        }
        if (request.sandbox() != null) {
            merchant.setSandbox(request.sandbox());
        }
        if (request.callbackUrl() != null) {
            merchant.setCallbackUrl(blankToNull(request.callbackUrl()));
        }
        if (request.active() != null) {
            merchant.setActive(request.active());
        }
        return merchantRepository.save(merchant);
    }

    /**
     * Autoatendimento: o próprio merchant cadastra as credenciais Mercado Pago. O access token é validado em
     * {@code GET /users/me} antes de salvar; campos de segredo vazios mantêm o valor atual.
     */
    @Transactional
    public Merchant updateOwnMercadoPago(UUID merchantId, String accessToken, String publicKey, String webhookSecret,
                                         Boolean sandbox) {
        Merchant merchant = getById(merchantId);
        if (merchant.isLegacy()) {
            throw new BusinessRuleException("legacy merchant credentials are managed by environment variables");
        }
        boolean newToken = StringUtils.hasText(accessToken);
        if (!newToken && merchant.getMercadoPagoAccessTokenEncrypted() == null) {
            throw new BusinessRuleException("mercado pago access token is required");
        }
        if (!StringUtils.hasText(publicKey)) {
            throw new BusinessRuleException("mercado pago public key is required");
        }
        String token = newToken ? accessToken.trim() : cipher.decrypt(merchant.getMercadoPagoAccessTokenEncrypted());
        boolean tokenIsTest = token.startsWith("TEST-");
        boolean keyIsTest = publicKey.trim().startsWith("TEST-");
        if (tokenIsTest != keyIsTest) {
            throw new BusinessRuleException("access token and public key must both be test or both be production credentials");
        }
        MercadoPagoAccount account = accountVerifier.apply(token);
        if (newToken) {
            merchant.setMercadoPagoAccessTokenEncrypted(cipher.encrypt(token));
        }
        merchant.setMercadoPagoPublicKey(publicKey.trim());
        if (StringUtils.hasText(webhookSecret)) {
            merchant.setMercadoPagoWebhookSecretEncrypted(cipher.encrypt(webhookSecret.trim()));
        }
        merchant.setSandbox(sandbox != null ? sandbox : tokenIsTest);
        merchant.markMercadoPagoVerified(account.id(), account.nickname(), java.time.LocalDateTime.now());
        return merchantRepository.save(merchant);
    }

    @Transactional
    public CreatedMerchant rotateApiKey(UUID id) {
        Merchant merchant = getById(id);
        String apiKey = assignNewApiKey(merchant);
        return new CreatedMerchant(merchantRepository.save(merchant), apiKey, null);
    }

    @Transactional
    public CreatedMerchant rotateCallbackSecret(UUID id) {
        Merchant merchant = getById(id);
        String callbackSecret = assignNewCallbackSecret(merchant);
        return new CreatedMerchant(merchantRepository.save(merchant), null, callbackSecret);
    }

    @Transactional(readOnly = true)
    public Merchant getById(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("merchant not found"));
    }

    @Transactional(readOnly = true)
    public List<Merchant> list() {
        return merchantRepository.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Credenciais para chamar o Mercado Pago em nome do merchant.
     */
    @Transactional(readOnly = true)
    public MerchantCredentials credentials(UUID merchantId) {
        Merchant merchant = getById(merchantId);
        String accessToken = cipher.decrypt(merchant.getMercadoPagoAccessTokenEncrypted());
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessRuleException("mercado pago access token is not configured for merchant");
        }
        return new MerchantCredentials(
                merchant.getId(),
                accessToken,
                merchant.getMercadoPagoPublicKey(),
                cipher.decrypt(merchant.getMercadoPagoWebhookSecretEncrypted()),
                merchant.isSandbox(),
                notificationUrl(merchant));
    }

    @Transactional(readOnly = true)
    public Optional<String> webhookSecret(UUID merchantId) {
        return Optional.ofNullable(cipher.decrypt(getById(merchantId).getMercadoPagoWebhookSecretEncrypted()))
                .filter(StringUtils::hasText);
    }

    @Transactional(readOnly = true)
    public Optional<CallbackTarget> callbackTarget(UUID merchantId) {
        Merchant merchant = getById(merchantId);
        if (!StringUtils.hasText(merchant.getCallbackUrl()) || merchant.getCallbackSecretEncrypted() == null) {
            return Optional.empty();
        }
        return Optional.of(new CallbackTarget(merchant.getCallbackUrl(), cipher.decrypt(merchant.getCallbackSecretEncrypted())));
    }

    public String notificationUrl(Merchant merchant) {
        if (merchant.isLegacy()) {
            return mercadoPagoProperties.notificationUrl();
        }
        return mercadoPagoProperties.webhookBaseUrl() + "/api/webhooks/mercado-pago/" + merchant.getId();
    }

    /**
     * Sincroniza o merchant legacy com a configuração do ambiente (INTERNAL_API_KEY e MERCADO_PAGO_*).
     */
    @Transactional
    public void syncLegacy(String apiKey, String accessToken, String publicKey, String webhookSecret, boolean sandbox) {
        Merchant merchant = getById(Merchant.LEGACY_ID);
        if (StringUtils.hasText(apiKey)) {
            merchant.setApiKeyHash(Hmac.sha256Hex(apiKey));
            merchant.setApiKeyPrefix(apiKey.substring(0, Math.min(6, apiKey.length())));
        }
        if (StringUtils.hasText(accessToken)) {
            merchant.setMercadoPagoAccessTokenEncrypted(cipher.encrypt(accessToken));
        }
        if (StringUtils.hasText(publicKey)) {
            merchant.setMercadoPagoPublicKey(publicKey);
        }
        if (StringUtils.hasText(webhookSecret)) {
            merchant.setMercadoPagoWebhookSecretEncrypted(cipher.encrypt(webhookSecret));
        }
        merchant.setSandbox(sandbox);
        merchantRepository.save(merchant);
    }

    private String assignNewApiKey(Merchant merchant) {
        String apiKey = API_KEY_PREFIX + Hmac.randomToken(24);
        merchant.setApiKeyHash(Hmac.sha256Hex(apiKey));
        merchant.setApiKeyPrefix(apiKey.substring(0, API_KEY_PREFIX.length() + 8));
        return apiKey;
    }

    private String assignNewCallbackSecret(Merchant merchant) {
        String secret = "whsec_" + Hmac.randomToken(24);
        merchant.setCallbackSecretEncrypted(cipher.encrypt(secret));
        return secret;
    }

    private String encryptOrNull(String value) {
        return StringUtils.hasText(value) ? cipher.encrypt(value.trim()) : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record CreatedMerchant(Merchant merchant, String apiKey, String callbackSecret) {
    }

    public record CallbackTarget(String url, String secret) {
    }
}
