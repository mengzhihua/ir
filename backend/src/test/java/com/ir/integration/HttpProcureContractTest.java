package com.ir.integration;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mock.MockEcosystemClient;
import com.ir.snapshot.FinanceSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpProcureContractTest {
    @Test
    void sapSnapshotsThenCreatePrByAlias() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://sap.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"SAP\",\"snapshots\":[{"
                                + "\"dataType\":\"STOCK\",\"bizKey\":\"M1099/1000/0001\","
                                + "\"status\":\"LOW\",\"sku\":\"MAT-1000\",\"qty\":3,"
                                + "\"plantCode\":\"1000\",\"title\":\"MAT-1000 1000/0001\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://sap.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andExpect(jsonPath("$.type").value("SAP_CREATE_PR"))
                .andExpect(jsonPath("$.targetKey").value("MAT-1000"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"banfn\":\"1000000099\",\"status\":\"CREATED\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "SAP,OA,SRM");
        CtSystem sap = system("SAP", "http://sap.local", "sap-open-key");
        List<Map<String, Object>> rows = factory.ecosystem(sap).fetchSnapshots();
        assertEquals(1, rows.size());
        assertEquals("M1099/1000/0001", rows.get(0).get("bizKey"));
        assertEquals("MAT-1000", rows.get(0).get("sku"));

        ActionCommand command = new ActionCommand();
        command.setType("SAP_CREATE_PR");
        command.setTargetKey("MAT-1000");
        factory.ecosystem(sap).execute(command);
        server.verify();
    }

    @Test
    void oaStartThenApproveTask() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://oa.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oa-open-key"))
                .andExpect(jsonPath("$.type").value("OA_START_WORKFLOW"))
                .andExpect(jsonPath("$.targetKey").value("MAT-1000"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"instanceNo\":\"WF-IR-1\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://oa.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oa-open-key"))
                .andExpect(jsonPath("$.type").value("OA_APPROVE_TASK"))
                .andExpect(jsonPath("$.targetKey").value("8801"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"APPROVED\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "SAP,OA,SRM");
        CtSystem oa = system("OA", "http://oa.local", "oa-open-key");
        ActionCommand start = new ActionCommand();
        start.setType("OA_START_WORKFLOW");
        start.setTargetKey("MAT-1000");
        factory.ecosystem(oa).execute(start);
        ActionCommand approve = new ActionCommand();
        approve.setType("OA_APPROVE_TASK");
        approve.setTargetKey("8801");
        factory.ecosystem(oa).execute(approve);
        server.verify();
    }

    @Test
    void srmSnapshotsThenExpeditePoByOpenIr() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://srm.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "srm-wms-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"SRM\",\"snapshots\":[{"
                                + "\"dataType\":\"ASN\",\"bizKey\":\"ASN-IR-DELAY\","
                                + "\"status\":\"DELAYED\",\"sku\":\"SKU001\",\"qty\":100,"
                                + "\"poCode\":\"PO-IR-EXPEDITE\",\"refCode\":\"PO-IR-EXPEDITE\","
                                + "\"plantCode\":\"P001\",\"title\":\"发货通知 ASN-IR-DELAY\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://srm.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "srm-wms-key"))
                .andExpect(jsonPath("$.type").value("SRM_EXPEDITE_PO"))
                .andExpect(jsonPath("$.targetKey").value("PO-IR-EXPEDITE"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"code\":\"PO-IR-EXPEDITE\",\"status\":\"CONFIRMED\"}}",
                        MediaType.APPLICATION_JSON));

        ClientFactory factory = factory(http, "SAP,OA,SRM");
        CtSystem srm = system("SRM", "http://srm.local", "srm-wms-key");
        List<?> asns = factory.srm(srm).fetchAsns();
        org.junit.jupiter.api.Assertions.assertEquals(1, asns.size());

        ActionCommand command = new ActionCommand();
        command.setType("SRM_EXPEDITE_PO");
        command.setTargetKey("PO-IR-EXPEDITE");
        factory.srm(srm).execute(command);
        server.verify();
    }

    @Test
    void sapStockUsesOpenIrWhenApiKeySet() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://sap.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"SAP\",\"snapshots\":[{"
                                + "\"dataType\":\"STOCK\",\"bizKey\":\"M1099/1000/0001\","
                                + "\"status\":\"LOW\",\"sku\":\"MAT-1000\",\"qty\":3,"
                                + "\"plantCode\":\"1000\"}]}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP");
        CtSystem sap = system("SAP", "http://sap.local", "sap-open-key");
        List<?> stock = factory.sap(sap).fetchStock();
        assertEquals(1, stock.size());
        server.verify();
    }

    @Test
    void sapFinanceUsesOpenIrWhenApiKeySet() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://sap.local/api/open/ir/snapshots"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"system\":\"SAP\",\"snapshots\":["
                                + "{\"dataType\":\"AP_OPEN\",\"bizKey\":\"IRFI0001/1\","
                                + "\"status\":\"OPEN\",\"amount\":18650},"
                                + "{\"dataType\":\"AR_OPEN\",\"bizKey\":\"IRFI0002/1\","
                                + "\"status\":\"OPEN\",\"amount\":24230},"
                                + "{\"dataType\":\"STOCK\",\"bizKey\":\"M1099/1000/0001\","
                                + "\"status\":\"LOW\",\"sku\":\"MAT-1000\",\"qty\":3,"
                                + "\"amount\":135,\"plantCode\":\"1000\"}]}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP");
        CtSystem sap = system("SAP", "http://sap.local", "sap-open-key");
        List<FinanceSnapshot> finance = factory.sap(sap).fetchFinance();
        assertEquals(3, finance.size());
        FinanceSnapshot ap = metric(finance, "AP_OPEN");
        assertEquals(1, ap.getItemCount().intValue());
        assertEquals(0, ap.getAmount().compareTo(BigDecimal.valueOf(18650.0)));
        FinanceSnapshot ar = metric(finance, "AR_OPEN");
        assertEquals(1, ar.getItemCount().intValue());
        assertEquals(0, ar.getAmount().compareTo(BigDecimal.valueOf(24230.0)));
        FinanceSnapshot stock = metric(finance, "STOCK_VALUE");
        assertEquals(1, stock.getItemCount().intValue());
        assertEquals(0, stock.getAmount().compareTo(BigDecimal.valueOf(135.0)));
        server.verify();
    }

    @Test
    void ecosystemExecuteForwardsIdempotencyKey() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://inv.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "inv-open-key"))
                .andExpect(jsonPath("$.type").value("INV_VERIFY_INPUT"))
                .andExpect(jsonPath("$.idempotencyKey").value("ACT-1"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"verifyStatus\":\"VERIFIED\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "INV");
        CtSystem inv = system("INV", "http://inv.local", "inv-open-key");
        ActionCommand command = new ActionCommand();
        command.setType("INV_VERIFY_INPUT");
        command.setTargetKey("10001001");
        command.setIdempotencyKey("ACT-1");
        factory.ecosystem(inv).execute(command);
        server.verify();
    }

    @Test
    void omsWmsTmsExecuteForwardIdempotencyKey() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://oms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oms-open-key"))
                .andExpect(jsonPath("$.type").value("OMS_HOLD"))
                .andExpect(jsonPath("$.idempotencyKey").value("ACT-OMS"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"HOLD\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://wms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "wms-open-key"))
                .andExpect(jsonPath("$.type").value("WMS_ALLOCATE"))
                .andExpect(jsonPath("$.idempotencyKey").value("ACT-WMS"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"ALLOCATED\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://tms.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "tms-open-key"))
                .andExpect(jsonPath("$.type").value("TMS_DISPATCH"))
                .andExpect(jsonPath("$.waybillCode").value("WB-IR-CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("ACT-TMS"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"DISPATCHED\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "OMS,WMS,TMS");
        ActionCommand oms = new ActionCommand();
        oms.setType("OMS_HOLD");
        oms.setTargetKey("IR-SO-STUCK");
        oms.setIdempotencyKey("ACT-OMS");
        factory.oms(system("OMS", "http://oms.local", "oms-open-key")).execute(oms);
        ActionCommand wms = new ActionCommand();
        wms.setType("WMS_ALLOCATE");
        wms.setTargetKey("SO-IR-STUCK");
        wms.setIdempotencyKey("ACT-WMS");
        factory.wms(system("WMS", "http://wms.local", "wms-open-key")).execute(wms);
        ActionCommand tms = new ActionCommand();
        tms.setType("TMS_DISPATCH");
        tms.setTargetKey("WB-IR-CREATED");
        tms.setIdempotencyKey("ACT-TMS");
        factory.tms(system("TMS", "http://tms.local", "tms-open-key")).execute(tms);
        server.verify();
    }

    @Test
    void invActionsMissingFallsBackToDedicatedPath() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://inv.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "inv-open-key"))
                .andExpect(jsonPath("$.type").value("INV_VERIFY_INPUT"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://inv.local/api/open/ir/verify-input"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "inv-open-key"))
                .andExpect(jsonPath("$.targetKey").value("10001001"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"verifyStatus\":\"VERIFIED\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "INV");
        ActionCommand command = new ActionCommand();
        command.setType("INV_VERIFY_INPUT");
        command.setTargetKey("10001001");
        factory.ecosystem(system("INV", "http://inv.local", "inv-open-key")).execute(command);
        server.verify();
    }

    @Test
    void srmLoginModePagesUseCurrent() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://srm.local/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"token\":\"srm-token\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://srm.local/api/purchase/order/page?current=1&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer srm-token"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":[{"
                        + "\"code\":\"PO-IR-1\",\"status\":\"CONFIRMED\",\"totalAmount\":100}]}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP,OA,SRM");
        CtSystem srm = system("SRM", "http://srm.local", null);
        srm.setUsername("admin");
        srm.setPassword("admin123");
        java.util.List<com.ir.snapshot.PurchaseSnapshot> orders = factory.srm(srm).fetchPurchaseOrders();
        assertEquals(1, orders.size());
        assertEquals("PO-IR-1", orders.get(0).getCode());
        server.verify();
    }

    @Test
    void srmActionsMissingFallsBackToPurchaseSuggest() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://srm.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "srm-wms-key"))
                .andExpect(jsonPath("$.type").value("SRM_PURCHASE_SUGGEST"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://srm.local/api/open/ir/purchase-suggest"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "srm-wms-key"))
                .andExpect(jsonPath("$.sku").value("SKU001"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"code\":\"PR-FALLBACK\",\"status\":\"DRAFT\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP,OA,SRM");
        ActionCommand command = new ActionCommand();
        command.setType("SRM_PURCHASE_SUGGEST");
        command.setTargetKey("SKU001");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<String, Object>();
        params.put("sku", "SKU001");
        params.put("qty", 5);
        command.setParams(params);
        command.setIdempotencyKey("SRM-SUG-FALLBACK");
        Map<String, Object> result = factory.srm(system("SRM", "http://srm.local", "srm-wms-key")).execute(command);
        assertEquals("PR-FALLBACK", result.get("code"));
        server.verify();
    }

    @Test
    void sapActionsMissingFallsBackToReleaseMo() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://sap.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andExpect(jsonPath("$.type").value("SAP_RELEASE_MO"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://sap.local/api/open/ir/release-mo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "sap-open-key"))
                .andExpect(jsonPath("$.targetKey").value("IR10000100"))
                .andExpect(jsonPath("$.aufnr").value("IR10000100"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"REL\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP,OA,BOM");
        ActionCommand command = new ActionCommand();
        command.setType("SAP_RELEASE_MO");
        command.setTargetKey("IR10000100");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<String, Object>();
        params.put("aufnr", "IR10000100");
        command.setParams(params);
        factory.ecosystem(system("SAP", "http://sap.local", "sap-open-key")).execute(command);
        server.verify();
    }

    @Test
    void bomActionsMissingFallsBackToImplementEcn() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://bom.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "bom-open-key"))
                .andExpect(jsonPath("$.type").value("BOM_IMPLEMENT_ECN"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://bom.local/api/open/ir/implement-ecn"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "bom-open-key"))
                .andExpect(jsonPath("$.targetKey").value("ECN-IR-APPROVED"))
                .andExpect(jsonPath("$.ecnNo").value("ECN-IR-APPROVED"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"status\":\"IMPLEMENTED\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP,OA,BOM");
        ActionCommand command = new ActionCommand();
        command.setType("BOM_IMPLEMENT_ECN");
        command.setTargetKey("ECN-IR-APPROVED");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<String, Object>();
        params.put("ecnNo", "ECN-IR-APPROVED");
        command.setParams(params);
        factory.ecosystem(system("BOM", "http://bom.local", "bom-open-key")).execute(command);
        server.verify();
    }

    @Test
    void oaActionsMissingFallsBackToStartWorkflow() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("http://oa.local/api/open/ir/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oa-open-key"))
                .andExpect(jsonPath("$.type").value("OA_START_WORKFLOW"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://oa.local/api/open/ir/start-workflow"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "oa-open-key"))
                .andExpect(jsonPath("$.targetKey").value("MAT-1000"))
                .andExpect(jsonPath("$.definitionCode").value("GENERAL"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"instanceNo\":\"WF-FALLBACK\"}}",
                        MediaType.APPLICATION_JSON));
        ClientFactory factory = factory(http, "SAP,OA,BOM");
        ActionCommand command = new ActionCommand();
        command.setType("OA_START_WORKFLOW");
        command.setTargetKey("MAT-1000");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<String, Object>();
        params.put("definitionCode", "GENERAL");
        command.setParams(params);
        factory.ecosystem(system("OA", "http://oa.local", "oa-open-key")).execute(command);
        server.verify();
    }
}

    private static ClientFactory factory(RestTemplate http, String systems) {
        return new ClientFactory(
                null, null, null, null, null, null, new MockEcosystemClient(),
                new com.ir.integration.client.BaseUrlValidator(true), http, systems);
    }

    private static FinanceSnapshot metric(List<FinanceSnapshot> rows, String name) {
        for (FinanceSnapshot row : rows) {
            if (name.equals(row.getMetric())) {
                return row;
            }
        }
        throw new AssertionError("缺少财务指标 " + name);
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
