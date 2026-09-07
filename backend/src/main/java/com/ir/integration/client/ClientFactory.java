package com.ir.integration.client;

import com.ir.integration.http.*;
import com.ir.integration.mock.*;
import org.springframework.stereotype.Component;
import com.ir.integration.entity.CtSystem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class ClientFactory {
    private final MockOmsClient oms;
    private final MockWmsClient wms;
    private final MockTmsClient tms;
    private final MockBmsClient bms;
    private final RestTemplate http;

    public ClientFactory(MockOmsClient oms, MockWmsClient wms, MockTmsClient tms, MockBmsClient bms) {
        this.oms = oms;
        this.wms = wms;
        this.tms = tms;
        this.bms = bms;
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.http = new RestTemplate(factory);
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
