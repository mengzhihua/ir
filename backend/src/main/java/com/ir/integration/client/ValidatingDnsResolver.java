package com.ir.integration.client;

import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ValidatingDnsResolver implements DnsResolver {
    private final BaseUrlValidator validator;

    public ValidatingDnsResolver(BaseUrlValidator validator) {
        this.validator = validator;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses =
                SystemDefaultDnsResolver.INSTANCE.resolve(host);
        if (validator.isPrivateHostsAllowed()) {
            return addresses;
        }
        for (InetAddress address : addresses) {
            try {
                validator.validateAddress(address);
            } catch (IllegalArgumentException ex) {
                UnknownHostException failure =
                        new UnknownHostException(ex.getMessage());
                failure.initCause(ex);
                throw failure;
            }
        }
        return addresses;
    }
}
