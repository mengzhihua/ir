package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.SrmClient;
import org.springframework.stereotype.Component;

@Component
public class MockSrmClient implements SrmClient {
    @Override
    public void execute(ActionCommand command) {
        if (command.getTargetKey() == null || command.getTargetKey().trim().isEmpty()) {
            throw new IllegalArgumentException("采购建议缺少 SKU");
        }
    }

    @Override
    public boolean health() {
        return true;
    }
}
