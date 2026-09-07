package com.ir.integration.client;

import com.ir.integration.http.*;
import com.ir.integration.mock.*;
import com.ir.integration.entity.CtSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

@Component
public class ClientFactory {
    private final MockOmsClient oms;
    private final MockWmsClient wms;
    private final MockTmsClient tms;
    private final MockBmsClient bms;
    private final BaseUrlValidator validator;
    private final RestTemplate http;

    @Autowired
    public ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            BaseUrlValidator validator) {
        this(oms, wms, tms, bms, validator, createHttp());
    }

    ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            BaseUrlValidator validator,
            RestTemplate http) {
        this.oms = oms;
        this.wms = wms;
        this.tms = tms;
        this.bms = bms;
        this.validator = validator;
        this.http = http;
        this.http.getInterceptors().add(this::validateRequest);
    }

    private static RestTemplate createHttp() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private ClientHttpResponse validateRequest(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        try {
            URI original = request.getURI();
            if (validator.isPrivateHostsAllowed()) {
                validator.validate(original.toString());
                return execution.execute(request, body);
            }
            validator.validateStructure(original);
            InetAddress[] addresses =
                    InetAddress.getAllByName(original.getHost());
            if (addresses.length == 0) {
                throw new IllegalArgumentException("baseUrl主机无法解析");
            }
            for (InetAddress address : addresses) {
                validator.validateAddress(address);
            }
            URI pinned = pinnedUri(original, addresses[0]);
            HttpRequestWrapper wrapped = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return pinned;
                }
            };
            wrapped.getHeaders().set("Host", hostHeader(original));
            return execution.execute(wrapped, body);
        } catch (IllegalArgumentException ex) {
            throw new IntegrationException("集成地址校验失败", ex);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IntegrationException("集成地址校验失败", ex);
        }
    }

    private URI pinnedUri(URI original, InetAddress address) {
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }
        StringBuilder value = new StringBuilder();
        value.append(original.getScheme()).append("://");
        if (original.getRawUserInfo() != null) {
            value.append(original.getRawUserInfo()).append("@");
        }
        value.append(host);
        if (original.getPort() >= 0) {
            value.append(":").append(original.getPort());
        }
        if (original.getRawPath() != null) {
            value.append(original.getRawPath());
        }
        if (original.getRawQuery() != null) {
            value.append("?").append(original.getRawQuery());
        }
        if (original.getRawFragment() != null) {
            value.append("#").append(original.getRawFragment());
        }
        return URI.create(value.toString());
    }

    private String hostHeader(URI original) {
        int port = original.getPort();
        boolean defaultPort = port < 0
                || ("http".equalsIgnoreCase(original.getScheme())
                && port == 80)
                || ("https".equalsIgnoreCase(original.getScheme())
                && port == 443);
        return defaultPort ? original.getHost()
                : original.getHost() + ":" + port;
    }

    public OmsClient oms(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpOmsClient(http, system.getBaseUrl(), system.getUsername(), system.getPassword());
        }
        return oms;
    }

    public WmsClient wms(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpWmsClient(http, system.getBaseUrl(), system.getUsername(), system.getPassword());
        }
        return wms;
    }

    public TmsClient tms(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpTmsClient(http, system.getBaseUrl());
        }
        return tms;
    }

    public BmsClient bms(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpBmsClient(http, system.getBaseUrl(), system.getApiKey());
        }
        return bms;
    }
}
