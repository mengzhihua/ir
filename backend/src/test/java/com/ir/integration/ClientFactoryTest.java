package com.ir.integration;

import com.ir.integration.client.BaseUrlValidator;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockBmsClient;
import com.ir.integration.mock.MockOmsClient;
import com.ir.integration.mock.MockTmsClient;
import com.ir.integration.mock.MockWmsClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ClientFactoryTest {
    @Test
    void rejectsLoopbackAtOutboundRequestTime() {
        ClientFactory factory = new ClientFactory(
                mock(MockOmsClient.class),
                mock(MockWmsClient.class),
                mock(MockTmsClient.class),
                mock(MockBmsClient.class),
                new BaseUrlValidator(false));
        CtSystem system = new CtSystem();
        system.setMode("HTTP");
        system.setBaseUrl("http://127.0.0.1:8090");

        assertThrows(IntegrationException.class,
                () -> factory.oms(system).fetchOrders());
    }
}
