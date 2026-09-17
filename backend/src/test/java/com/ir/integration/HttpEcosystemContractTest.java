package com.ir.integration;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockEcosystemClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpEcosystemContractTest {
    @Test
    void forcedHttpPullsSnapshotsAndPostsCreatePr() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://sap.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"SAP\",\"snapshots\":["
                                + "{\"dataType\":\"STOCK\",\"bizKey\":\"M1099/1000/0001\","
                                + "\"status\":\"LOW\",\"sku\":\"MAT-1000\",\"qty\":3,"
                                + "\"plantCode\":\"1000\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://sap.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andExpect(jsonPath("$.type").value("SAP_CREATE_PR"))
                .andExpect(jsonPath("$.targetKey").value("MAT-1000"))
                .andExpect(jsonPath("$.sku").value("MAT-1000"))
                .andExpect(jsonPath("$.qty").value(16))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"banfn\":\"PR0099\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = new ClientFactory(
                null, null, null, null, null, new MockEcosystemClient(), http, "SAP,OA,SRM");
        CtSystem sap = new CtSystem();
        sap.setCode("SAP");
        sap.setMode("MOCK");
        sap.setBaseUrl("http://sap.local/");
        sap.setApiKey("sap-open-key");

        List<Map<String, Object>> rows = factory.ecosystem(sap).fetchSnapshots();
        assertEquals(1, rows.size());
        assertEquals("LOW", rows.get(0).get("status"));
        assertEquals("MAT-1000", rows.get(0).get("sku"));

        ActionCommand command = new ActionCommand();
        command.setType("SAP_CREATE_PR");
        command.setTargetKey("MAT-1000");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("sku", "MAT-1000");
        params.put("qty", 16);
        params.put("plantCode", "1000");
        command.setParams(params);
        factory.ecosystem(sap).execute(command);
        server.verify();
    }

    @Test
    void oaApprovePostsTaskId() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://oa.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oa-open-key"))
                .andExpect(jsonPath("$.type").value("OA_APPROVE_TASK"))
                .andExpect(jsonPath("$.targetKey").value("8801"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"APPROVED\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = new ClientFactory(
                null, null, null, null, null, new MockEcosystemClient(), http, "");
        CtSystem oa = new CtSystem();
        oa.setCode("OA");
        oa.setMode("HTTP");
        oa.setBaseUrl("http://oa.local");
        oa.setApiKey("oa-open-key");
        ActionCommand command = new ActionCommand();
        command.setType("OA_APPROVE_TASK");
        command.setTargetKey("8801");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", "8801");
        params.put("comment", "IR 控制塔系统审批");
        command.setParams(params);
        factory.ecosystem(oa).execute(command);
        server.verify();
        assertTrue(ClientFactory.forcedHttp("SAP,OA", "OA"));
    }
}
