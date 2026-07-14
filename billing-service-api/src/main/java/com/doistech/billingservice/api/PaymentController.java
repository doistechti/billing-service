package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.PaymentResponse;
import com.doistech.billingservice.core.payment.Payment;
import com.doistech.billingservice.core.payment.PaymentQueryService;
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
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Consultas de pagamentos processados.")
@SecurityRequirement(name = "apiKeyAuth")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    public PaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pagamento por ID", description = "Retorna os dados de um pagamento identificado internamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento encontrado.",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Pagamento nao encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaymentResponse getById(
            @Parameter(description = "Identificador interno do pagamento.", example = "65ca0d73-c3eb-4d3d-a93d-8f298c3d2c51")
            @PathVariable UUID id) {
        return toResponse(paymentQueryService.getById(id));
    }

    @GetMapping("/invoice/{invoiceId}")
    @Operation(summary = "Listar pagamentos por fatura", description = "Retorna os pagamentos associados a uma fatura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pagamentos retornada com sucesso.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public List<PaymentResponse> listByInvoice(
            @Parameter(description = "Identificador interno da fatura.", example = "3a0c2c30-4c90-44d3-9621-1bd20ce4bc9c")
            @PathVariable UUID invoiceId) {
        return paymentQueryService.listByInvoice(invoiceId).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getCharge().getId(),
                payment.getGateway(),
                payment.getGatewayPaymentId(),
                payment.getGatewayStatus(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}
