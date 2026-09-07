package com.ir.integration.client;

import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockBmsClient;
import com.ir.integration.mock.MockOmsClient;
import com.ir.integration.mock.MockTmsClient;
import com.ir.integration.mock.MockWmsClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        system.setBaseUrl("http://localhost:8090");

        assertThrows(IntegrationException.class,
                () -> factory.oms(system).fetchOrders());
    }

    @Test
    void pinsValidatedAddressAndPreservesHostHeader() {
        BaseUrlValidator validator = mock(BaseUrlValidator.class);
        when(validator.isPrivateHostsAllowed()).thenReturn(false);
        doNothing().when(validator).validateAddress(any(InetAddress.class));
        RestTemplate restTemplate = new RestTemplate();
        ClientFactory factory = new ClientFactory(
                mock(MockOmsClient.class),
                mock(MockWmsClient.class),
                mock(MockTmsClient.class),
                mock(MockBmsClient.class),
                validator,
                restTemplate);
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(
                        "http://127.0.0.1:8090/api/auth/login"))
                .andExpect(header("Host", "localhost:8090"))
                .andRespond(withSuccess(
                        "{\"data\":{\"token\":\"test-token\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://127.0.0.1:8090/api/order/page?page=1&size=200"))
                .andExpect(header("Host", "localhost:8090"))
                .andRespond(withSuccess(
                        "{\"data\":[]}", MediaType.APPLICATION_JSON));

        CtSystem system = new CtSystem();
        system.setMode("HTTP");
        system.setBaseUrl("http://localhost:8090");
        factory.oms(system).fetchOrders();

        server.verify();
    }
}
