package com.ir.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockEcosystemClient;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOtwbContractTest {
    @Test
    void omsSnapshotsThenHold() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://oms.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "oms-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"OMS\",\"orders\":[{"
                                + "\"orderNo\":\"IR-SO-STUCK\",\"status\":\"AUDITED\","
                                + "\"warehouseCode\":\"WH-SH\",\"qty\":2,\"priority\":10,"
                                + "\"orderTime\":\"2026-09-16 22:00:00\"}],"
                                + "\"inventory\":[{\"sku\":\"SKU001\",\"warehouseCode\":\"WH-SH\","
                                + "\"qtyOnHand\":500,\"qtyAvailable\":500,\"safetyQty\":10}],"
                                + "\"sales\":[{\"salesDate\":\"2026-09-16\",\"sku\":\"SKU001\","
                                + "\"warehouseCode\":\"WH-SH\",\"channelCode\":\"TMALL\",\"qty\":2,\"amount\":398}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://oms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oms-open-key"))
                .andExpect(jsonPath("$.type").value("OMS_HOLD"))
                .andExpect(jsonPath("$.targetKey").value("IR-SO-STUCK"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"HOLD\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "OMS,WMS,TMS");
        CtSystem oms = system("OMS", "http://oms.local/", "oms-open-key");
        List<OrderSnapshot> orders = factory.oms(oms).fetchOrders();
        assertEquals(1, orders.size());
        assertEquals("IR-SO-STUCK", orders.get(0).getOrderNo());
        assertEquals("AUDITED", orders.get(0).getStatus());
        assertEquals(Integer.valueOf(10), orders.get(0).getPriority());

        ActionCommand command = new ActionCommand();
        command.setType("OMS_HOLD");
        command.setTargetKey("IR-SO-STUCK");
        factory.oms(oms).execute(command);
        server.verify();
    }

    @Test
    void wmsSnapshotsThenAllocate() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://wms.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "wms-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"WMS\",\"outbound\":[{"
                                + "\"code\":\"SO-IR-STUCK\",\"externalNo\":\"IR-SO-STUCK\","
                                + "\"warehouseCode\":\"WH01\",\"status\":\"NEW\",\"totalQty\":2}],"
                                + "\"inventory\":[{\"sku\":\"SKU001\",\"itemCode\":\"SKU001\","
                                + "\"warehouseCode\":\"WH01\",\"qtyOnHand\":200,\"qtyAvailable\":200}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://wms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "wms-open-key"))
                .andExpect(jsonPath("$.type").value("WMS_ALLOCATE"))
                .andExpect(jsonPath("$.targetKey").value("SO-IR-STUCK"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"ALLOCATED\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "OMS,WMS,TMS");
        CtSystem wms = system("WMS", "http://wms.local", "wms-open-key");
        List<WmsOrderSnapshot> outbound = factory.wms(wms).fetchOutbound();
        assertEquals(1, outbound.size());
        assertEquals("SO-IR-STUCK", outbound.get(0).getCode());
        assertEquals("NEW", outbound.get(0).getStatus());

        ActionCommand command = new ActionCommand();
        command.setType("WMS_ALLOCATE");
        command.setTargetKey("SO-IR-STUCK");
        factory.wms(wms).execute(command);
        server.verify();
    }

    @Test
    void tmsSnapshotsThenSyncTrack() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://tms.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "tms-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"TMS\",\"waybills\":[{"
                                + "\"waybillCode\":\"WB-IR-DELAY\",\"code\":\"WB-IR-DELAY\","
                                + "\"sourceNo\":\"IR-SO-STUCK\",\"carrierCode\":\"SF\","
                                + "\"status\":\"IN_TRANSIT\",\"plannedArriveTime\":\"2026-09-17 06:00:00\","
                                + "\"exceptionFlag\":false}],\"bills\":[]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://tms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "tms-open-key"))
                .andExpect(jsonPath("$.type").value("TMS_SYNC_TRACK"))
                .andExpect(jsonPath("$.targetKey").value("WB-IR-DELAY"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"code\":\"WB-IR-DELAY\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "OMS,WMS,TMS");
        CtSystem tms = system("TMS", "http://tms.local", "tms-open-key");
        List<ShipmentSnapshot> waybills = factory.tms(tms).fetchWaybills();
        assertEquals(1, waybills.size());
        assertEquals("WB-IR-DELAY", waybills.get(0).getWaybillCode());
        assertEquals("IR-SO-STUCK", waybills.get(0).getSourceNo());

        ActionCommand command = new ActionCommand();
        command.setType("TMS_SYNC_TRACK");
        command.setTargetKey("WB-IR-DELAY");
        factory.tms(tms).execute(command);
        server.verify();
    }

    private static ClientFactory factory(RestTemplate http, String systems) {
        return new ClientFactory(
                null, null, null, null, null, null, new MockEcosystemClient(),
                new com.ir.integration.client.BaseUrlValidator(true), http, systems);
    }

    private static CtSystem system(String code, String url, String apiKey) {
        CtSystem system = new CtSystem();
        system.setCode(code);
        system.setMode("MOCK");
        system.setBaseUrl(url);
        system.setApiKey(apiKey);
        return system;
    }
}
