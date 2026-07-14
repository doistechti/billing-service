package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Endpoint de verificacao basica de disponibilidade.")
public class HealthController {

    @GetMapping
    @Operation(summary = "Health check", description = "Retorna o status basico do servico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servico disponivel.",
                    content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    })
    public HealthResponse health() {
        return new HealthResponse("UP", "billing-service");
    }
}
