package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.CallbackDeliveryResponse;
import com.doistech.billingservice.api.MerchantApi.MerchantPublicConfigResponse;
import com.doistech.billingservice.api.MerchantApi.UpdateOwnMercadoPagoRequest;
import com.doistech.billingservice.callback.CallbackDelivery;
import com.doistech.billingservice.callback.CallbackQueue;
import com.doistech.billingservice.callback.CallbackStatus;
import com.doistech.billingservice.core.merchant.AuthenticatedMerchant;
import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.core.merchant.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Merchant", description = "Configuracao e callbacks do merchant autenticado.")
@SecurityRequirement(name = "apiKeyAuth")
public class MerchantController {

    private final MerchantService merchantService;
    private final CallbackQueue callbackQueue;

    public MerchantController(MerchantService merchantService, CallbackQueue callbackQueue) {
        this.merchantService = merchantService;
        this.callbackQueue = callbackQueue;
    }

    @GetMapping("/api/merchant")
    @Operation(summary = "Configuracao publica", description = "Inclui a public key do Mercado Pago para tokenizar cartao no navegador.")
    public MerchantPublicConfigResponse me(@AuthenticationPrincipal AuthenticatedMerchant authenticated) {
        return toConfig(merchantService.getById(authenticated.id()));
    }

    @PutMapping("/api/merchant/mercado-pago")
    @Operation(summary = "Cadastrar credenciais Mercado Pago", description = """
            Autoatendimento do merchant: valida o access token no Mercado Pago (GET /users/me) e salva cifrado.
            Não disponível para o merchant legacy (credenciais vêm do ambiente).""")
    public MerchantPublicConfigResponse updateMercadoPago(
            @AuthenticationPrincipal AuthenticatedMerchant authenticated,
            @Valid @RequestBody UpdateOwnMercadoPagoRequest request) {
        return toConfig(merchantService.updateOwnMercadoPago(authenticated.id(), request.accessToken(),
                request.publicKey(), request.webhookSecret(), request.sandbox()));
    }

    private MerchantPublicConfigResponse toConfig(Merchant merchant) {
        return new MerchantPublicConfigResponse(
                merchant.getId(),
                merchant.getCode(),
                merchant.getName(),
                merchant.getMercadoPagoPublicKey(),
                merchant.isSandbox(),
                merchant.getCallbackUrl() != null,
                merchant.getMercadoPagoAccessTokenEncrypted() != null,
                merchant.getMercadoPagoWebhookSecretEncrypted() != null,
                merchantService.notificationUrl(merchant),
                merchant.getMercadoPagoAccountId(),
                merchant.getMercadoPagoAccountNickname(),
                merchant.getMercadoPagoVerifiedAt());
    }

    @GetMapping("/api/callbacks")
    @Operation(summary = "Listar callbacks", description = "Ultimos 100 callbacks, opcionalmente filtrados por status.")
    public List<CallbackDeliveryResponse> callbacks(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @RequestParam(required = false) CallbackStatus status) {
        return callbackQueue.list(merchant.id(), status).stream().map(MerchantController::toResponse).toList();
    }

    @PostMapping("/api/callbacks/{id}/redeliver")
    @Operation(summary = "Reenviar callback", description = "Recoloca o callback na fila com novas tentativas.")
    public CallbackDeliveryResponse redeliver(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable UUID id) {
        return toResponse(callbackQueue.redeliver(merchant.id(), id));
    }

    private static CallbackDeliveryResponse toResponse(CallbackDelivery delivery) {
        return new CallbackDeliveryResponse(
                delivery.getId(),
                delivery.getEventType(),
                delivery.getChargeId(),
                delivery.getStatus().name(),
                delivery.getAttempts(),
                delivery.getNextAttemptAt(),
                delivery.getLastStatusCode(),
                delivery.getLastError(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt());
    }
}
