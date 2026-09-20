package com.ir.integration;

import com.ir.integration.client.BaseUrlValidator;
import com.ir.integration.client.ValidatingDnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatingDnsResolverTest {
    @Test
    void rejectsLoopbackHostnameWhenPrivateHostsAreDisabled() {
        ValidatingDnsResolver resolver = new ValidatingDnsResolver(
                new BaseUrlValidator(false));

        assertThrows(UnknownHostException.class,
                () -> resolver.resolve("localhost"));
    }

    @Test
    void resolvesHostnameWhenPrivateHostsAreAllowed() {
        ValidatingDnsResolver resolver = new ValidatingDnsResolver(
                new BaseUrlValidator(true));

        assertDoesNotThrow(() -> {
            InetAddress[] addresses = resolver.resolve("localhost");
            if (addresses.length == 0) {
                throw new UnknownHostException("localhost未解析到地址");
            }
        });
    }
}
