package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.mock.MockWmsClient;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UncertainDispatchTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InventorySnapshotMapper inventoryMapper;
    @Autowired CtActionMapper actionMapper;
    @SpyBean MockWmsClient wms;

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("token").asText();
    }

    private static final String BODY =
            "{\"type\":\"WMS_REPLENISH\",\"targetKey\":\"WH-BJ\",\"params\":{\"sku\":\"SKU002\",\"qty\":3,"
            + "\"warehouseCode\":\"WH-BJ\",\"fromWarehouseCode\":\"WH-SH\"}}";

    @Test
    void unknownOutcomeKeepsReservationAndRetryReusesKey() throws Exception {
        String t = token();
        mvc.perform(post("/api/integration/sync/WMS").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(true));
        BigDecimal from = available("WH-SH", "SKU002");

        doThrow(new IntegrationException("read timeout", new SocketTimeoutException(), true))
                .when(wms).execute(any(ActionCommand.class));
        String resp = mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(resp).get("data").get("id").asLong();
        String actionNo = mapper.readTree(resp).get("data").get("actionNo").asText();
        assertEquals(from.subtract(BigDecimal.valueOf(3)).setScale(2), available("WH-SH", "SKU002"));

        doThrow(new IntegrationException("connection refused", new java.net.ConnectException(), false))
                .when(wms).execute(any(ActionCommand.class));
        String failed = mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"sku\"", "\"idempotencyKey\":\"client-chosen\",\"sku\"")))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(from.subtract(BigDecimal.valueOf(3)).setScale(2), available("WH-SH", "SKU002"));
        long failedId = mapper.readTree(failed).get("data").get("id").asLong();
        String failedNo = mapper.readTree(failed).get("data").get("actionNo").asText();
        assertEquals(failedNo, mapper.readTree(failed).get("data").get("params")
                .get(ActionService.IDEMPOTENCY_KEY).asText());

        mvc.perform(post("/api/action/" + id + "/retry").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.code").value(1));

        doNothing().when(wms).execute(any(ActionCommand.class));
        String retried = mvc.perform(post("/api/action/" + failedId + "/retry")
                .header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(failedNo, mapper.readTree(retried).get("data").get("params")
                .get(ActionService.IDEMPOTENCY_KEY).asText());
        assertEquals(failedNo, mapper.readTree(retried).get("data").get("params")
                .get(ActionService.RETRY_OF).asText());
        assertEquals("RETRIED", actionMapper.selectById(failedId).getStatus());
        BigDecimal afterRetry = available("WH-SH", "SKU002");

        mvc.perform(post("/api/action/" + failedId + "/retry").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(afterRetry, available("WH-SH", "SKU002"));

        CtAction legacy = actionMapper.selectById(failedId);
        legacy.setId(null);
        legacy.setActionNo("ACT-LEGACY-1");
        legacy.setStatus("FAILED");
        legacy.setParams("{\"sku\":\"SKU002\",\"qty\":1,\"warehouseCode\":\"WH-BJ\","
                + "\"fromWarehouseCode\":\"WH-SH\"}");
        actionMapper.insert(legacy);
        String legacyRetried = mvc.perform(post("/api/action/" + legacy.getId() + "/retry")
                .header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        assertEquals("ACT-LEGACY-1", mapper.readTree(legacyRetried).get("data").get("params")
                .get(ActionService.IDEMPOTENCY_KEY).asText());
    }

    private BigDecimal available(String warehouse, String sku) {
        InventorySnapshot row = inventoryMapper.selectOne(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "WMS")
                .eq(InventorySnapshot::getWarehouseCode, warehouse)
                .eq(InventorySnapshot::getSku, sku));
        return row.getQtyAvailable().setScale(2);
    }
}
