package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.CreateInvoiceRequest;
import com.doistech.billingservice.api.BillingApi.InvoiceItemResponse;
import com.doistech.billingservice.api.BillingApi.InvoiceResponse;
import com.doistech.billingservice.core.invoice.Invoice;
import com.doistech.billingservice.core.invoice.InvoiceService;
import com.doistech.billingservice.shared.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices", description = "Operacoes de criacao, consulta e cancelamento de faturas.")
@SecurityRequirement(name = "apiKeyAuth")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar fatura", description = "Cria uma fatura com um ou mais itens para um cliente existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fatura criada com sucesso.",
                    content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido ou regra de negocio violada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return toResponse(invoiceService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar fatura por ID", description = "Retorna os dados completos de uma fatura pelo identificador interno.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fatura encontrada.",
                    content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Fatura nao encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InvoiceResponse getById(
            @Parameter(description = "Identificador interno da fatura.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            @PathVariable UUID id) {
        return toResponse(invoiceService.getById(id));
    }

    @GetMapping("/by-reference")
    @Operation(summary = "Buscar fatura por referencia externa",
            description = "Consulta uma fatura usando a dupla sourceSystem + externalReference.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fatura encontrada.",
                    content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Fatura nao encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InvoiceResponse getByReference(
            @Parameter(description = "Sistema de origem da fatura.", example = "erp")
            @RequestParam String sourceSystem,
            @Parameter(description = "Referencia externa da fatura no sistema de origem.", example = "invoice-2026-0001")
            @RequestParam String externalReference
    ) {
        return toResponse(invoiceService.getByReference(sourceSystem, externalReference));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar fatura", description = "Cancela uma fatura existente, desde que esteja em um estado cancelavel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fatura cancelada com sucesso.",
                    content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fatura nao pode ser cancelada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Fatura nao encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InvoiceResponse cancel(
            @Parameter(description = "Identificador interno da fatura.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            @PathVariable UUID id) {
        return toResponse(invoiceService.cancel(id));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomer().getId(),
                invoice.getSourceSystem(),
                invoice.getExternalReference(),
                invoice.getDescription(),
                invoice.getTotalAmount(),
                invoice.getDueDate(),
                invoice.getPaidAt(),
                invoice.getCanceledAt(),
                invoice.getStatus(),
                invoice.getItems().stream()
                        .map(item -> new InvoiceItemResponse(
                                item.getId(),
                                item.getDescription(),
                                item.getQuantity(),
                                item.getUnitAmount(),
                                item.getTotalAmount()))
                        .toList()
        );
    }
}
