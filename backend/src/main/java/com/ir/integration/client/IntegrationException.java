package com.ir.integration.client;

public class IntegrationException extends RuntimeException {
    /** 请求已发出但结果未知(如读超时),对端可能已受理. */
    private final boolean outcomeUnknown;

    public IntegrationException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public IntegrationException(String message, Throwable cause, boolean outcomeUnknown) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public IntegrationException(String message) {
        this(message, null, false);
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
