package com.doistech.billingservice.shared;

public class GatewayIntegrationException extends RuntimeException {

    public GatewayIntegrationException(String message) {
        super(message);
    }

    public GatewayIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
