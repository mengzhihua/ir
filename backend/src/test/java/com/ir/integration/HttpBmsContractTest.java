package com.ir.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockEcosystemClient;
import com.ir.snapshot.entity.CostRecord;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpBmsContractTest {
    @Test
    void fetchCostsFromOpenRecords() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        LocalDate from = LocalDate.of(2026, 9, 16);
        LocalDate to = LocalDate.of(2026, 9, 17);
        server.expect(requestTo("http://bms.local/api/open/cost/records?from=2026-09-16&to=2026-09-17"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "bms-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":[{\"bizDate\":\"2026-09-17\",\"orderNo\":\"IR-SO-STUCK\","
                                + "\"warehouseCode\":\"WH-SH\",\"costType\":\"OUTBOUND\",\"amount\":88.0},"
                                + "{\"bizDate\":\"2026-09-17\",\"orderNo\":\"IR-SO-STUCK\","
                                + "\"warehouseCode\":\"WH-SH\",\"costType\":\"TRANSPORT\",\"amount\":36.0,"
                                + "\"carrierCode\":\"SF\"}]}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = new ClientFactory(
                null, null, null, null, null, new MockEcosystemClient(), http, "BMS");
        CtSystem bms = new CtSystem();
        bms.setCode("BMS");
        bms.setMode("MOCK");
        bms.setBaseUrl("http://bms.local");
        bms.setApiKey("bms-open-key");
        List<CostRecord> rows = factory.bms(bms).fetchCosts(from, to);
        assertEquals(2, rows.size());
        assertEquals("IR-SO-STUCK", rows.get(0).getOrderNo());
        assertEquals("WH-SH", rows.get(0).getWarehouseCode());
        assertEquals("OUTBOUND", rows.get(0).getCostType());
        assertEquals("FREIGHT", rows.get(1).getCostType());
        assertEquals("SF", rows.get(1).getCarrierCode());
        server.verify();
    }
}
