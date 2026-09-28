package com.ir.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-password;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "ir.admin-password=Str0ngPass!"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPasswordBootstrapTest {
    @Autowired
    private TestRestTemplate rest;

    @LocalServerPort
    private int port;

    @Test
    void configuredInitialPasswordReplacesDefault() {
        String url = "http://localhost:" + port + "/api/auth/login";
        ResponseEntity<String> configured = rest.postForEntity(
                url, Map.of("username", "admin", "password", "Str0ngPass!"), String.class);
        ResponseEntity<String> defaultPassword = rest.postForEntity(
                url, Map.of("username", "admin", "password", "admin123"), String.class);

        assertTrue(configured.getBody().contains("\"code\":0"));
        assertTrue(defaultPassword.getBody().contains("\"code\":1"));
    }
}
