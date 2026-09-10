package com.ir.integration.client;

import com.ir.integration.http.*;
import com.ir.integration.mock.*;
import com.ir.integration.entity.CtSystem;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;

@Component
public class ClientFactory {
    private final MockOmsClient oms;
    private final MockWmsClient wms;
    private final MockTmsClient tms;
    private final MockBmsClient bms;
    private final MockSrmClient srm;
    private final MockSapClient sap;
    private final BaseUrlValidator validator;
    private final RestTemplate http;

    @Autowired
    public ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            MockSrmClient srm,
            MockSapClient sap,
            BaseUrlValidator validator) {
        this(oms, wms, tms, bms, srm, sap, validator, null);
    }

    ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            MockSrmClient srm,
            MockSapClient sap,
            BaseUrlValidator validator,
            RestTemplate http) {
        this.oms = oms;
        this.wms = wms;
        this.tms = tms;
        this.bms = bms;
        this.srm = srm;
        this.sap = sap;
        this.validator = validator;
        this.http = http == null ? createHttp() : http;
        this.http.getInterceptors().add(this::validateRequest);
    }

    private RestTemplate createHttp() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(3000)
                .setSocketTimeout(10000)
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setDnsResolver(new ValidatingDnsResolver(validator))
                .disableRedirectHandling()
                .build();
        return new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(client));
    }

    private ClientHttpResponse validateRequest(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        try {
            validator.validateStructure(request.getURI());
        } catch (IllegalArgumentException ex) {
            throw new IntegrationException("集成地址校验失败", ex);
        }
        return execution.execute(request, body);
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

    public SrmClient srm(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpSrmClient(http, system.getBaseUrl(), system.getUsername(), system.getPassword());
        }
        return srm;
    }

    public SapClient sap(CtSystem system) {
        if ("HTTP".equalsIgnoreCase(system.getMode())) {
            return new HttpSapClient(http, system.getBaseUrl(), system.getUsername(), system.getPassword());
        }
        return sap;
    }
}
