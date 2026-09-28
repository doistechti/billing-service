package com.doistech.billingservice.shared;

import org.springframework.web.client.RestClientResponseException;

public class GatewayIntegrationException extends RuntimeException {

    public GatewayIntegrationException(String message) {
        super(message);
    }

    public GatewayIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Falha que pode ter sucesso numa nova tentativa: erro 5xx/429 do gateway ou falha de rede.
     */
    public boolean isTransient() {
        Throwable cause = getCause();
        if (cause instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        return cause != null;
    }
}
