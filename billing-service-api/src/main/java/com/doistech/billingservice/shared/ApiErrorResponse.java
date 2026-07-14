package com.doistech.billingservice.shared;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "ApiErrorResponse", description = "Formato padrao de erro da API.")
public record ApiErrorResponse(
        @Schema(description = "Data/hora do erro.", example = "2026-07-13T10:15:30")
        LocalDateTime timestamp,
        @Schema(description = "Codigo HTTP retornado.", example = "400")
        int status,
        @Schema(description = "Descricao textual do status HTTP.", example = "Bad Request")
        String error,
        @Schema(description = "Mensagem principal do erro.", example = "validation failed")
        String message,
        @ArraySchema(arraySchema = @Schema(description = "Lista de detalhes de validacao ou regra de negocio."))
        List<String> details
) {
}
