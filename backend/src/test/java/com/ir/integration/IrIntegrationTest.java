package com.ir.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    private String token() throws Exception {String s=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin123\"}")).andReturn().getResponse().getContentAsString();return mapper.readTree(s).get("data").get("token").asText();}
    @Test void loginAndCoreFlow() throws Exception {String t=token();mvc.perform(get("/api/tower/overview").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));mvc.perform(get("/api/trace/page?current=1&size=10").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isArray()).andExpect(jsonPath("$.data.total").value(300));mvc.perform(post("/api/action").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OMS_HOLD\",\"targetKey\":\"SO000043\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));mvc.perform(post("/api/sandbox/baseline").header("Authorization","Bearer "+t)).andExpect(status().isOk());mvc.perform(post("/api/forecast/run").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU001\",\"horizon\":14,\"method\":\"AUTO\"}")).andExpect(status().isOk());}
    @Test void autoSandboxRanksAndAppliesPending() throws Exception {
        String t = token();
        String body = mvc.perform(post("/api/sandbox/auto/run").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runNo").isString())
                .andExpect(jsonPath("$.data.recommended.id").isNumber())
                .andExpect(jsonPath("$.data.scenarios.length()").value(13))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = mapper.readTree(body).get("data");
        long id = data.get("recommended").get("id").asLong();
        mvc.perform(get("/api/sandbox/auto/latest").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended.id").value(id));
        mvc.perform(get("/api/sandbox/scenario/page?kind=AUTO&size=20").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(13)));
        mvc.perform(post("/api/sandbox/scenario/" + id + "/apply?execute=false")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/tower/overview").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendation.id").value(id));
    }

    @Test void ecosystemSystemsSyncAndAct() throws Exception {
        String t = token();
        String systems = mvc.perform(get("/api/integration/system").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(11)))
                .andReturn().getResponse().getContentAsString();
        JsonNode list = mapper.readTree(systems).get("data");
        java.util.Set<String> codes = new java.util.HashSet<>();
        list.forEach(n -> codes.add(n.get("code").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(codes.containsAll(
                java.util.Arrays.asList("OMS", "WMS", "TMS", "BMS", "SRM", "SAP", "BOM", "INV", "CRM", "DMS", "OA")));
        mvc.perform(post("/api/integration/sync/SAP").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(get("/api/integration/snapshot/page?systemCode=SAP&size=20")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAP_CREATE_PR\",\"targetKey\":\"MAT-1000\",\"params\":{\"qty\":10}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SRM_PURCHASE_SUGGEST\",\"targetKey\":\"SKU001\",\"params\":{\"qty\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mvc.perform(get("/api/tower/overview").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ecosystem.SAP").isMap())
                .andExpect(jsonPath("$.data.kpi.sapLowStock").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test void coordinationFansOutAcrossSapSrmOaAndTraceShowsRisks() throws Exception {
        String t = token();
        mvc.perform(post("/api/alert/evaluate").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        String alerts = mvc.perform(get("/api/alert/page?size=200").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sapAlertId = null;
        for (JsonNode row : mapper.readTree(alerts).get("data").get("records")) {
            if ("SAP_LOW_STOCK".equals(row.get("ruleCode").asText())) {
                sapAlertId = row.get("id").asLong();
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(sapAlertId, "应产生 SAP 低库存预警");
        String executed = mvc.perform(post("/api/alert/" + sapAlertId + "/execute-suggested")
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andReturn().getResponse().getContentAsString();
        java.util.Set<String> types = new java.util.HashSet<>();
        java.util.Set<String> systems = new java.util.HashSet<>();
        for (JsonNode action : mapper.readTree(executed).get("data")) {
            types.add(action.get("type").asText());
            systems.add(action.get("targetSystem").asText());
            if ("WMS_REPLENISH".equals(action.get("type").asText())) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        action.get("targetKey").asText().startsWith("WH"),
                        "仓内补货目标应是仓库编码而不是工厂号");
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(types.contains("SAP_CREATE_PR"));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains("SRM_PURCHASE_SUGGEST"));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains("OA_START_WORKFLOW"));
        org.junit.jupiter.api.Assertions.assertTrue(systems.contains("SAP"));
        org.junit.jupiter.api.Assertions.assertTrue(systems.contains("SRM"));
        org.junit.jupiter.api.Assertions.assertTrue(systems.contains("OA"));

        mvc.perform(post("/api/forecast/replenish/to-action").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU001\",\"warehouseCode\":\"WH-SH\",\"qty\":8,\"suggestQty\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        mvc.perform(get("/api/trace/SO000010").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplyRisks").isArray())
                .andExpect(jsonPath("$.data.supplyRisks.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.ecosystem").isArray());
    }
}
