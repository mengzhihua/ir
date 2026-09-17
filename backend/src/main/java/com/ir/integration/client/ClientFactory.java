package com.ir.integration.client;

import com.ir.integration.entity.CtSystem;
import com.ir.integration.http.HttpBmsClient;
import com.ir.integration.http.HttpOmsClient;
import com.ir.integration.http.HttpSrmClient;
import com.ir.integration.http.HttpTmsClient;
import com.ir.integration.http.HttpWmsClient;
import com.ir.integration.mock.MockBmsClient;
import com.ir.integration.mock.MockOmsClient;
import com.ir.integration.mock.MockSrmClient;
import com.ir.integration.mock.MockTmsClient;
import com.ir.integration.mock.MockWmsClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ClientFactory {
    private final MockOmsClient oms;
    private final MockWmsClient wms;
    private final MockTmsClient tms;
    private final MockBmsClient bms;
    private final MockSrmClient srm;
    private final RestTemplate http = new RestTemplate();

    public ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            MockSrmClient srm) {
        this.oms = oms;
        this.wms = wms;
        this.tms = tms;
        this.bms = bms;
        this.srm = srm;
    }

    public OmsClient oms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpOmsClient(
                    http, system.getBaseUrl(), system.getUsername(),
                    system.getPassword(), system.getApiKey());
        }
        return oms;
    }

    public WmsClient wms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpWmsClient(
                    http, system.getBaseUrl(), system.getUsername(),
                    system.getPassword(), system.getApiKey());
        }
        return wms;
    }

    public TmsClient tms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpTmsClient(http, system.getBaseUrl());
        }
        return tms;
    }

    public BmsClient bms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpBmsClient(http, system.getBaseUrl(), system.getApiKey());
        }
        return bms;
    }

    public SrmClient srm(CtSystem system) {
        if (httpMode(system)) {
            return new HttpSrmClient(http, system.getBaseUrl(), system.getApiKey());
        }
        return srm;
    }

    private static boolean httpMode(CtSystem system) {
        return system != null && "HTTP".equalsIgnoreCase(system.getMode());
    }
}
