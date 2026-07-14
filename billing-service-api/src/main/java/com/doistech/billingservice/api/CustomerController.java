package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.CreateCustomerRequest;
import com.doistech.billingservice.api.BillingApi.CustomerResponse;
import com.doistech.billingservice.core.customer.Customer;
import com.doistech.billingservice.core.customer.CustomerService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Operacoes de cadastro e consulta de clientes.")
@SecurityRequirement(name = "apiKeyAuth")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar cliente", description = "Cadastra um novo cliente usando a referencia do sistema de origem.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente criado com sucesso.",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload invalido ou regra de negocio violada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente.")
    })
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return toResponse(customerService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID", description = "Retorna os dados de um cliente a partir do identificador interno.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado.",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "401", description = "API key invalida ou ausente."),
            @ApiResponse(responseCode = "404", description = "Cliente nao encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CustomerResponse getById(
            @Parameter(description = "Identificador interno do cliente.", example = "8d56f5a6-fb5d-4d1c-a8f4-1831fd9f8d15")
            @PathVariable UUID id) {
        return toResponse(customerService.getById(id));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getSourceSystem(),
                customer.getExternalReference(),
                customer.getName(),
                customer.getEmail(),
                customer.isActive()
        );
    }
}
