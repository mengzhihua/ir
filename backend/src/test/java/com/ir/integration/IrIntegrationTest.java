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
                .andExpect(jsonPath("$.data.scenarios.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = mapper.readTree(body).get("data");
        long id = data.get("recommended").get("id").asLong();
        mvc.perform(get("/api/sandbox/auto/latest").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended.id").value(id));
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
}
