package com.doistech.billingservice.api;

import com.doistech.billingservice.api.MerchantApi.CreateMerchantRequest;
import com.doistech.billingservice.api.MerchantApi.MerchantResponse;
import com.doistech.billingservice.api.MerchantApi.MerchantSecretsResponse;
import com.doistech.billingservice.api.MerchantApi.UpdateMerchantRequest;
import com.doistech.billingservice.core.merchant.Merchant;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.core.merchant.MerchantService.CreatedMerchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/merchants")
@Tag(name = "Admin · Merchants", description = "Cadastro de contas recebedoras. Exige X-Admin-Key.")
@SecurityRequirement(name = "adminKeyAuth")
public class MerchantAdminController {

    private final MerchantService merchantService;

    public MerchantAdminController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar merchant", description = "Devolve a API key e o segredo de callback uma unica vez.")
    public MerchantSecretsResponse create(@Valid @RequestBody CreateMerchantRequest request) {
        return toSecrets(merchantService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar merchants")
    public List<MerchantResponse> list() {
        return merchantService.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar merchant")
    public MerchantResponse get(@PathVariable UUID id) {
        return toResponse(merchantService.getById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar merchant", description = "Campos nulos nao sao alterados; string vazia remove o valor.")
    public MerchantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMerchantRequest request) {
        return toResponse(merchantService.update(id, request));
    }

    @PostMapping("/{id}/rotate-api-key")
    @Operation(summary = "Gerar nova API key", description = "A chave anterior deixa de funcionar imediatamente.")
    public MerchantSecretsResponse rotateApiKey(@PathVariable UUID id) {
        return toSecrets(merchantService.rotateApiKey(id));
    }

    @PostMapping("/{id}/rotate-callback-secret")
    @Operation(summary = "Gerar novo segredo de callback")
    public MerchantSecretsResponse rotateCallbackSecret(@PathVariable UUID id) {
        return toSecrets(merchantService.rotateCallbackSecret(id));
    }

    private MerchantSecretsResponse toSecrets(CreatedMerchant created) {
        return new MerchantSecretsResponse(toResponse(created.merchant()), created.apiKey(), created.callbackSecret());
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getCode(),
                merchant.getName(),
                merchant.getApiKeyPrefix(),
                merchant.getMercadoPagoAccessTokenEncrypted() != null,
                merchant.getMercadoPagoPublicKey(),
                merchant.getMercadoPagoWebhookSecretEncrypted() != null,
                merchant.isSandbox(),
                merchant.getCallbackUrl(),
                merchantService.notificationUrl(merchant),
                merchant.isActive(),
                merchant.getCreatedAt());
    }
}
