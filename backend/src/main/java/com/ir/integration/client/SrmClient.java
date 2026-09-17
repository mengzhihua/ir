package com.ir.integration.client;

public interface SrmClient {
    void execute(ActionCommand command);

    boolean health();
}
