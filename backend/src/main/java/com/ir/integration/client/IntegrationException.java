package com.ir.integration.client;

public class IntegrationException extends RuntimeException {
    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IntegrationException(String message) {
        super(message);
    }
}
