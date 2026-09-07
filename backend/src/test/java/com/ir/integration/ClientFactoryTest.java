package com.ir.integration;

import com.ir.integration.client.BaseUrlValidator;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockBmsClient;
import com.ir.integration.mock.MockOmsClient;
import com.ir.integration.mock.MockTmsClient;
import com.ir.integration.mock.MockWmsClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ClientFactoryTest {
    @Test
    void preservesHostnameAndDoesNotFollowRedirects() throws IOException {
        AtomicReference<String> host = new AtomicReference<>();
        AtomicInteger redirected = new AtomicInteger();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/auth/login", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            respond(exchange, 200, "{\"data\":{\"token\":\"test-token\"}}");
        });
        server.createContext("/api/order/page", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            exchange.getResponseHeaders().set(
                    "Location", "http://localhost:" + port + "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirected.incrementAndGet();
            respond(exchange, 200, "{\"data\":[]}");
        });
        server.start();
        try {
            ClientFactory factory = new ClientFactory(
                    mock(MockOmsClient.class),
                    mock(MockWmsClient.class),
                    mock(MockTmsClient.class),
                    mock(MockBmsClient.class),
                    new BaseUrlValidator(true));
            CtSystem system = new CtSystem();
            system.setMode("HTTP");
            system.setBaseUrl("http://localhost:" + port);

            factory.oms(system).fetchOrders();

            assertEquals("localhost:" + port, host.get());
            assertEquals(0, redirected.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidRequestStructureIsRejectedBeforeExecution() {
        ClientFactory factory = new ClientFactory(
                mock(MockOmsClient.class),
                mock(MockWmsClient.class),
                mock(MockTmsClient.class),
                mock(MockBmsClient.class),
                new BaseUrlValidator(false));
        CtSystem system = new CtSystem();
        system.setMode("HTTP");
        system.setBaseUrl("file:///tmp/ir");

        assertThrows(IntegrationException.class,
                () -> factory.oms(system).fetchOrders());
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
