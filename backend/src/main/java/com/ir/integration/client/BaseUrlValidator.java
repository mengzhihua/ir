package com.ir.integration.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class BaseUrlValidator {
    private final boolean allowPrivateHosts;

    public BaseUrlValidator(
            @Value("${ir.integration.allow-private-hosts:true}")
            boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    public void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl不能为空");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("baseUrl格式不正确", ex);
        }
        validateStructure(uri);
        if (allowPrivateHosts) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                validateAddress(address);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("baseUrl主机无法解析", ex);
        }
    }

    void validateStructure(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("baseUrl必须使用http或https");
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl必须包含主机名");
        }
    }

    public void validateAddress(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("baseUrl主机无法解析");
        }
        byte[] bytes = address.getAddress();
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || isPrivateIpv4(bytes) || isUniqueLocal(bytes)
                || isMetadata(bytes)) {
            throw new IllegalArgumentException(
                    "baseUrl不允许指向私有、回环、链路本地或云元数据地址");
        }
    }

    public boolean isPrivateHostsAllowed() {
        return allowPrivateHosts;
    }

    private boolean isMetadata(byte[] bytes) {
        return bytes.length == 4
                && (bytes[0] & 0xff) == 169
                && (bytes[1] & 0xff) == 254;
    }

    private boolean isPrivateIpv4(byte[] bytes) {
        if (bytes.length != 4) {
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private boolean isUniqueLocal(byte[] bytes) {
        return bytes.length == 16 && ((bytes[0] & 0xff) & 0xfe) == 0xfc;
    }
}
