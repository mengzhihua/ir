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
    @Test void loginAndCoreFlow() throws Exception {String t=token();mvc.perform(get("/api/tower/overview").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));mvc.perform(get("/api/trace/page?current=1&size=10").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isArray()).andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(300)));mvc.perform(post("/api/action").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OMS_HOLD\",\"targetKey\":\"SO000043\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));mvc.perform(post("/api/sandbox/baseline").header("Authorization","Bearer "+t)).andExpect(status().isOk());mvc.perform(post("/api/forecast/run").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU001\",\"horizon\":14,\"method\":\"AUTO\"}")).andExpect(status().isOk());}
    @Test void autoSandboxRanksAndAppliesPending() throws Exception {
        String t = token();
        String body = mvc.perform(post("/api/sandbox/auto/run").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runNo").isString())
                .andExpect(jsonPath("$.data.recommended.id").isNumber())
                .andExpect(jsonPath("$.data.scenarios.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.data.rationale.rule").value("SERVICE_FIRST_CASH_ROBUST"))
                .andExpect(jsonPath("$.data.rationale.reason").isString())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = mapper.readTree(body).get("data");
        long id = data.get("recommended").get("id").asLong();
        mvc.perform(get("/api/sandbox/auto/latest").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended.id").value(id))
                .andExpect(jsonPath("$.data.rationale.rule").value("SERVICE_FIRST_CASH_ROBUST"));
        mvc.perform(get("/api/sandbox/auto/history").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/sandbox/scenario/page?kind=AUTO&size=20").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
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

    @Test void policyUpdateChangesStanceAndOverview() throws Exception {
        String t = token();
        try {
            mvc.perform(put("/api/sandbox/policy").header("Authorization", "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"costWeight\":0.8,\"efficiencyWeight\":0.2,\"reevaluate\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.stance").value("COST"))
                    .andExpect(jsonPath("$.data.superseded").isNumber());
            mvc.perform(get("/api/sandbox/policy").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stance").value("COST"))
                    .andExpect(jsonPath("$.data.safetyDays").isNumber())
                    .andExpect(jsonPath("$.data.replenishLeadDays").isNumber());
            mvc.perform(get("/api/tower/overview").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.policy.stance").value("COST"))
                    .andExpect(jsonPath("$.data.kpi.pendingActions").isNumber())
                    .andExpect(jsonPath("$.data.kpi.carrierMix").isMap());
            String alerts = mvc.perform(get("/api/alert/page?type=ORDER_STUCK&status=OPEN&size=50")
                            .header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            boolean foundHold = false;
            for (JsonNode row : mapper.readTree(alerts).get("data").get("records")) {
                if ("OMS_STUCK".equals(row.path("ruleCode").asText())) {
                    org.junit.jupiter.api.Assertions.assertEquals("OMS_HOLD",
                            row.path("suggestedAction").asText());
                    foundHold = true;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(foundHold);
            String wms = mvc.perform(get("/api/alert/page?type=WMS_STUCK&status=OPEN&size=50")
                            .header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            boolean foundWmsHold = false;
            for (JsonNode row : mapper.readTree(wms).get("data").get("records")) {
                if ("WMS_STUCK".equals(row.path("ruleCode").asText())) {
                    org.junit.jupiter.api.Assertions.assertEquals("OMS_HOLD",
                            row.path("suggestedAction").asText());
                    foundWmsHold = true;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(foundWmsHold);
        } finally {
            mvc.perform(put("/api/sandbox/policy").header("Authorization", "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"costWeight\":0.5,\"efficiencyWeight\":0.5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stance").value("BALANCED"));
        }
    }
    @Test void phaseTwoObjectiveAndBalanceFlow() throws Exception {
        String t=token();
        mvc.perform(post("/api/integration/sync/SRM").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.purchaseOrders").value(24));
        mvc.perform(post("/api/integration/sync/SAP").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(10));
        mvc.perform(get("/api/objective/scoreboard").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.objectives.length()").value(5)).andExpect(jsonPath("$.data.metrics.npsEstimate").exists());
        mvc.perform(post("/api/balance/run").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.run.status").value("DONE")).andExpect(jsonPath("$.data.decisions").isArray());
        mvc.perform(post("/api/action").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"SRM_PURCHASE_SUGGEST\",\"targetKey\":\"SKU005\",\"params\":{\"sku\":\"SKU005\",\"qty\":80}}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mvc.perform(get("/api/supply/overview").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.suppliers").isArray());
    }

    @Test void towerCommandQueueAndExecute() throws Exception {
        String t = token();
        String body = mvc.perform(get("/api/tower/overview").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.command.nextActions").isArray())
                .andExpect(jsonPath("$.data.command.counts.total").isNumber())
                .andExpect(jsonPath("$.data.objectives.score").isNumber())
                .andExpect(jsonPath("$.data.balance.pendingDecisions").isNumber())
                .andExpect(jsonPath("$.data.supply.delayedAsn").isNumber())
                .andExpect(jsonPath("$.data.kpi.objectiveScore").isNumber())
                .andExpect(jsonPath("$.data.kpi.nextActions").isNumber())
                .andReturn().getResponse().getContentAsString();
        JsonNode alert = null;
        for (JsonNode row : mapper.readTree(body).path("data").path("command").path("nextActions")) {
            if ("ALERT".equals(row.path("kind").asText())) {
                alert = row;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(alert, "总览队列应包含可执行预警");
        mvc.perform(post("/api/tower/command").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ALERT\",\"id\":" + alert.path("id").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kind").value("ALERT"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        mvc.perform(post("/api/tower/command").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ALERT\",\"id\":" + alert.path("id").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("已处理")));
        mvc.perform(post("/api/tower/command").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ALERT\",\"id\":1.9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("整数")));
        mvc.perform(post("/api/tower/command").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"UNKNOWN\",\"id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
}
