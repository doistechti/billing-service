package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.BoletoResponse;
import com.doistech.billingservice.api.BillingApi.ChargeResponse;
import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.api.BillingApi.PixResponse;
import com.doistech.billingservice.api.BillingApi.RefundChargeRequest;
import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeMethod;
import com.doistech.billingservice.core.charge.ChargeService;
import com.doistech.billingservice.core.merchant.AuthenticatedMerchant;
import com.doistech.billingservice.shared.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charges")
@Tag(name = "Charges", description = "Operacoes de geracao e consulta de cobrancas.")
@SecurityRequirement(name = "apiKeyAuth")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar cobranca", description = """
            Gera uma cobranca para uma fatura. Sem `method`, cria Checkout Pro e devolve `paymentUrl`.
            PIX devolve QR Code; CREDIT_CARD usa o token do MP.js e responde aprovado ou recusado;
            BOLETO devolve linha digitavel e PDF. Repetir o header Idempotency-Key devolve a mesma cobranca.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cobranca criada com sucesso.",
                    content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido, regra de negocio violada ou recusa do gateway.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key ja usada para outra fatura.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Gateway indisponivel; tente novamente com a mesma Idempotency-Key.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChargeResponse create(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @Parameter(description = "Chave de idempotencia gerada pelo sistema cliente.")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateChargeRequest request) {
        return toResponse(chargeService.create(merchant.id(), request, idempotencyKey));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cobranca por ID", description = "Retorna os dados de uma cobranca especifica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cobranca encontrada.",
                    content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Cobranca nao encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChargeResponse getById(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @Parameter(description = "Identificador interno da cobranca.") @PathVariable UUID id) {
        return toResponse(chargeService.getById(merchant.id(), id));
    }

    @GetMapping("/invoice/{invoiceId}")
    @Operation(summary = "Listar cobrancas por fatura", description = "Retorna todas as cobrancas associadas a uma fatura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de cobrancas retornada com sucesso.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChargeResponse.class)))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public List<ChargeResponse> listByInvoice(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @Parameter(description = "Identificador interno da fatura.") @PathVariable UUID invoiceId) {
        return chargeService.listByInvoice(merchant.id(), invoiceId).stream().map(ChargeController::toResponse).toList();
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sincronizar com o gateway", description = "Consulta o gateway e aplica o status atual da cobranca.")
    public ChargeResponse sync(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable UUID id) {
        return toResponse(chargeService.sync(merchant.id(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar cobranca pendente", description = "Cancela Pix, boleto ou Checkout Pro ainda nao pago.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cobranca cancelada.",
                    content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cobranca nao esta pendente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChargeResponse cancel(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable UUID id) {
        return toResponse(chargeService.cancel(merchant.id(), id));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Estornar cobranca paga", description = "Estorno total (sem amount) ou parcial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estorno registrado.",
                    content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cobranca nao paga ou valor acima do saldo.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChargeResponse refund(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RefundChargeRequest request) {
        return toResponse(chargeService.refund(
                merchant.id(), id, request == null ? null : request.amount(), idempotencyKey));
    }

    static ChargeResponse toResponse(Charge charge) {
        PixResponse pix = charge.getMethod() == ChargeMethod.PIX
                ? new PixResponse(charge.getPixQrCode(), charge.getPixQrCodeBase64(), charge.getTicketUrl())
                : null;
        BoletoResponse boleto = charge.getMethod() == ChargeMethod.BOLETO
                ? new BoletoResponse(charge.getBoletoBarcode(), charge.getBoletoDigitableLine(), charge.getTicketUrl())
                : null;
        return new ChargeResponse(
                charge.getId(),
                charge.getInvoice().getId(),
                charge.getGateway(),
                charge.getGatewayPreferenceId(),
                charge.getGatewayPaymentId(),
                charge.getPaymentUrl(),
                charge.getStatus(),
                charge.getAmount(),
                charge.getMethod(),
                charge.getStatusDetail(),
                charge.getInstallments(),
                pix,
                boleto,
                charge.getExpiresAt(),
                charge.getPaidAt(),
                charge.getRefundedAmount(),
                charge.getCreatedAt()
        );
    }
}
