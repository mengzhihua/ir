package com.ir.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BalanceGuardrailTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("token").asText();
    }

    private String planner(String admin) throws Exception {
        mvc.perform(post("/api/system/user").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"planner_g\",\"password\":\"pw123456\",\"role\":\"PLANNER\",\"realName\":\"P\"}"));
        return login("planner_g", "pw123456");
    }

    @Test
    void configBoundsAndAdminOnly() throws Exception {
        String admin = login("admin", "admin123");
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"maxAutoExecutePerRun\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"serviceGuardAttainment\":1.5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cooldownHours\":12}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cooldownHours").value(12));
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SUGGEST\",\"cooldownHours\":0}"))
                .andExpect(jsonPath("$.code").value(1));
        mvc.perform(get("/api/balance/config").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.cooldownHours").value(12));
        String planner = planner(admin);
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + planner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"AUTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void offModeRejectsManualRunAndAutoOnlyExecutesLowRisk() throws Exception {
        String admin = login("admin", "admin123");
        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OFF\"}"))
                .andExpect(jsonPath("$.data.mode").value("OFF"));
        mvc.perform(post("/api/balance/run").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        mvc.perform(post("/api/balance/config").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"AUTO\"}"));
        String body = mvc.perform(post("/api/balance/run").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode decisions = mapper.readTree(body).get("data").get("decisions");
        Long pendingId = null;
        for (JsonNode d : decisions) {
            String status = d.get("status").asText();
            if (!"PENDING".equals(status) && !"REJECTED".equals(status)) {
                org.junit.jupiter.api.Assertions.assertEquals("LOW", d.get("riskLevel").asText(),
                        "只有 LOW 风险决策允许自动执行: " + d);
            }
            if ("PENDING".equals(status) && pendingId == null) {
                pendingId = d.get("id").asLong();
            }
        }
        if (pendingId != null) {
            mvc.perform(post("/api/balance/decision/" + pendingId + "/approve")
                    .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            mvc.perform(post("/api/balance/decision/" + pendingId + "/approve")
                    .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        }
        mvc.perform(get("/api/balance/overview").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }
}
