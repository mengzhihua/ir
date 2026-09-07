package com.ir.integration.client;

import com.ir.integration.http.*;
import com.ir.integration.mock.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ClientFactory {
    private final MockOmsClient oms; private final MockWmsClient wms; private final MockTmsClient tms; private final MockBmsClient bms;
    private final RestTemplate http = new RestTemplate();
    public ClientFactory(MockOmsClient oms, MockWmsClient wms, MockTmsClient tms, MockBmsClient bms) { this.oms=oms;this.wms=wms;this.tms=tms;this.bms=bms; }
    public OmsClient oms(String mode, String base) { return "HTTP".equalsIgnoreCase(mode) ? new HttpOmsClient(http, base) : oms; }
    public WmsClient wms(String mode, String base) { return "HTTP".equalsIgnoreCase(mode) ? new HttpWmsClient(http, base) : wms; }
    public TmsClient tms(String mode, String base) { return "HTTP".equalsIgnoreCase(mode) ? new HttpTmsClient(http, base) : tms; }
    public BmsClient bms(String mode, String base) { return "HTTP".equalsIgnoreCase(mode) ? new HttpBmsClient(http, base) : bms; }
}
