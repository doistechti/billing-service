package com.doistech.billingservice.core.merchant;

import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant extends BaseEntity {

    public static final UUID LEGACY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Gateway gateway = Gateway.MERCADO_PAGO;

    @Column(name = "api_key_hash", length = 64)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @Column(name = "mp_access_token_encrypted")
    private String mercadoPagoAccessTokenEncrypted;

    @Column(name = "mp_public_key")
    private String mercadoPagoPublicKey;

    @Column(name = "mp_webhook_secret_encrypted")
    private String mercadoPagoWebhookSecretEncrypted;

    @Column(name = "pm_secret_key_encrypted")
    private String pagarMeSecretKeyEncrypted;

    @Column(name = "pm_public_key")
    private String pagarMePublicKey;

    @Column(name = "pm_webhook_secret_encrypted")
    private String pagarMeWebhookSecretEncrypted;

    @Column(name = "pm_account_id", length = 60)
    private String pagarMeAccountId;

    @Column(name = "pm_account_name", length = 120)
    private String pagarMeAccountName;

    @Column(name = "pm_verified_at")
    private java.time.LocalDateTime pagarMeVerifiedAt;

    @Column(nullable = false)
    private boolean sandbox;

    @Column(name = "mp_account_id", length = 40)
    private String mercadoPagoAccountId;

    @Column(name = "mp_account_nickname", length = 120)
    private String mercadoPagoAccountNickname;

    @Column(name = "mp_verified_at")
    private java.time.LocalDateTime mercadoPagoVerifiedAt;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "callback_secret_encrypted")
    private String callbackSecretEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    public boolean isLegacy() {
        return LEGACY_ID.equals(getId());
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public String getMercadoPagoAccessTokenEncrypted() {
        return mercadoPagoAccessTokenEncrypted;
    }

    public void setMercadoPagoAccessTokenEncrypted(String mercadoPagoAccessTokenEncrypted) {
        this.mercadoPagoAccessTokenEncrypted = mercadoPagoAccessTokenEncrypted;
    }

    public String getMercadoPagoPublicKey() {
        return mercadoPagoPublicKey;
    }

    public void setMercadoPagoPublicKey(String mercadoPagoPublicKey) {
        this.mercadoPagoPublicKey = mercadoPagoPublicKey;
    }

    public String getMercadoPagoWebhookSecretEncrypted() {
        return mercadoPagoWebhookSecretEncrypted;
    }

    public void setMercadoPagoWebhookSecretEncrypted(String mercadoPagoWebhookSecretEncrypted) {
        this.mercadoPagoWebhookSecretEncrypted = mercadoPagoWebhookSecretEncrypted;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getCallbackSecretEncrypted() {
        return callbackSecretEncrypted;
    }

    public void setCallbackSecretEncrypted(String callbackSecretEncrypted) {
        this.callbackSecretEncrypted = callbackSecretEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMercadoPagoAccountId() {
        return mercadoPagoAccountId;
    }

    public String getMercadoPagoAccountNickname() {
        return mercadoPagoAccountNickname;
    }

    public java.time.LocalDateTime getMercadoPagoVerifiedAt() {
        return mercadoPagoVerifiedAt;
    }

    public void markMercadoPagoVerified(String accountId, String nickname, java.time.LocalDateTime at) {
        this.mercadoPagoAccountId = accountId;
        this.mercadoPagoAccountNickname = nickname;
        this.mercadoPagoVerifiedAt = at;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public String getPagarMeSecretKeyEncrypted() {
        return pagarMeSecretKeyEncrypted;
    }

    public void setPagarMeSecretKeyEncrypted(String pagarMeSecretKeyEncrypted) {
        this.pagarMeSecretKeyEncrypted = pagarMeSecretKeyEncrypted;
    }

    public String getPagarMePublicKey() {
        return pagarMePublicKey;
    }

    public void setPagarMePublicKey(String pagarMePublicKey) {
        this.pagarMePublicKey = pagarMePublicKey;
    }

    public String getPagarMeWebhookSecretEncrypted() {
        return pagarMeWebhookSecretEncrypted;
    }

    public void setPagarMeWebhookSecretEncrypted(String pagarMeWebhookSecretEncrypted) {
        this.pagarMeWebhookSecretEncrypted = pagarMeWebhookSecretEncrypted;
    }

    public String getPagarMeAccountId() {
        return pagarMeAccountId;
    }

    public String getPagarMeAccountName() {
        return pagarMeAccountName;
    }

    public java.time.LocalDateTime getPagarMeVerifiedAt() {
        return pagarMeVerifiedAt;
    }

    public void markPagarMeVerified(String accountId, String name, java.time.LocalDateTime at) {
        this.pagarMeAccountId = accountId;
        this.pagarMeAccountName = name;
        this.pagarMeVerifiedAt = at;
    }
}
