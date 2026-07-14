package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.ChargeResponse;
import com.doistech.billingservice.api.BillingApi.CreateChargeRequest;
import com.doistech.billingservice.core.charge.Charge;
import com.doistech.billingservice.core.charge.ChargeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    @Operation(summary = "Criar cobranca", description = "Gera uma cobranca para uma fatura usando um gateway configurado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cobranca criada com sucesso.",
                    content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido ou regra de negocio violada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public ChargeResponse create(@Valid @RequestBody CreateChargeRequest request) {
        return toResponse(chargeService.create(request));
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
            @Parameter(description = "Identificador interno da cobranca.", example = "e05a5fd0-48dd-4d6c-8f2a-3d7a6af88bde")
            @PathVariable UUID id) {
        return toResponse(chargeService.getById(id));
    }

    @GetMapping("/invoice/{invoiceId}")
    @Operation(summary = "Listar cobrancas por fatura", description = "Retorna todas as cobrancas associadas a uma fatura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de cobrancas retornada com sucesso.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChargeResponse.class)))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public List<ChargeResponse> listByInvoice(
            @Parameter(description = "Identificador interno da fatura.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            @PathVariable UUID invoiceId) {
        return chargeService.listByInvoice(invoiceId).stream().map(this::toResponse).toList();
    }

    private ChargeResponse toResponse(Charge charge) {
        return new ChargeResponse(
                charge.getId(),
                charge.getInvoice().getId(),
                charge.getGateway(),
                charge.getGatewayPreferenceId(),
                charge.getGatewayPaymentId(),
                charge.getPaymentUrl(),
                charge.getStatus(),
                charge.getAmount()
        );
    }
}
