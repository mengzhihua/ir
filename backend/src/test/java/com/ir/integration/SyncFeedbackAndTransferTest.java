package com.ir.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncFeedbackAndTransferTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InventorySnapshotMapper inventoryMapper;
    @Autowired CtSyncLogMapper syncLogMapper;

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("token").asText();
    }

    @Test
    void srmSapSyncRecordsLogsAndLastSyncAt() throws Exception {
        String t = token();
        mvc.perform(post("/api/integration/sync/SRM").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(post("/api/integration/sync/SAP").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(get("/api/integration/sync-log/page?systemCode=SRM&status=SUCCESS").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.total", greaterThan(0)));
        mvc.perform(get("/api/integration/sync-log/page?systemCode=SAP&status=SUCCESS").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.total", greaterThan(0)));
        mvc.perform(get("/api/integration/system").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data[?(@.code=='SRM')].lastSyncAt", hasItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.data[?(@.code=='SAP')].lastSyncAt", hasItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void sapSyncDoesNotWriteWmsInventoryLog() throws Exception {
        String t = token();
        Long before = syncLogMapper.selectCount(new LambdaQueryWrapper<CtSyncLog>()
                .eq(CtSyncLog::getSystemCode, "WMS").eq(CtSyncLog::getDataType, "INVENTORY"));
        mvc.perform(post("/api/integration/sync/SAP").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(true));
        Long after = syncLogMapper.selectCount(new LambdaQueryWrapper<CtSyncLog>()
                .eq(CtSyncLog::getSystemCode, "WMS").eq(CtSyncLog::getDataType, "INVENTORY"));
        assertEquals(before, after);
    }

    @Test
    void unsupportedSystemSyncFails() throws Exception {
        String t = token();
        mvc.perform(post("/api/integration/system").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ERP2\",\"name\":\"ERP2\",\"mode\":\"MOCK\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/integration/sync/ERP2").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(false));
        mvc.perform(get("/api/integration/system").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data[?(@.code=='ERP2' && @.lastSyncAt)]").isEmpty());
    }

    @Test
    void userListIgnoresBlankRoleFilter() throws Exception {
        String t = token();
        mvc.perform(get("/api/system/user?role=&keyword=&current=1&size=20").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThan(0)));
    }

    @Test
    void transferReservesSourceAndRejectsInsufficientStock() throws Exception {
        String t = token();
        mvc.perform(post("/api/integration/sync/WMS").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.data.ok").value(true));
        BigDecimal from = available(t, "WH-SH", "SKU001");
        BigDecimal to = available(t, "WH-BJ", "SKU001");
        String body = "{\"type\":\"WMS_REPLENISH\",\"targetKey\":\"WH-BJ\",\"params\":{\"sku\":\"SKU001\",\"qty\":5,"
                + "\"warehouseCode\":\"WH-BJ\",\"fromWarehouseCode\":\"WH-SH\"}}";
        mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertEquals(from.subtract(BigDecimal.valueOf(5)).setScale(2), available(t, "WH-SH", "SKU001"));
        assertEquals(to.add(BigDecimal.valueOf(5)).setScale(2), available(t, "WH-BJ", "SKU001"));

        String tooMuch = body.replace("\"qty\":5", "\"qty\":999999");
        mvc.perform(post("/api/action").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(tooMuch))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        assertEquals(from.subtract(BigDecimal.valueOf(5)).setScale(2), available(t, "WH-SH", "SKU001"));
    }

    private BigDecimal available(String t, String warehouse, String sku) {
        InventorySnapshot row = inventoryMapper.selectOne(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "WMS")
                .eq(InventorySnapshot::getWarehouseCode, warehouse)
                .eq(InventorySnapshot::getSku, sku));
        return row.getQtyAvailable().setScale(2);
    }
}
