package com.ir.integration;

import com.ir.integration.client.BaseUrlValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseUrlValidatorTest {
    private final BaseUrlValidator validator = new BaseUrlValidator(false);

    @Test
    void rejectsPrivateIpv4Ranges() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://10.1.2.3"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://172.16.1.1"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://192.168.1.1"));
    }

    @Test
    void rejectsUniqueLocalIpv6() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://[fc00::1]"));
    }

    @Test
    void allowsPublicHostWhenPrivateHostsAreDisabled() {
        assertDoesNotThrow(() -> validator.validate("https://example.com"));
    }
}
