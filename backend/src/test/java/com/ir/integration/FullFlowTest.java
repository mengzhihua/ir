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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullFlowTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OrderSnapshotMapper orderMapper;
    @Autowired
    private InventorySnapshotMapper inventoryMapper;
    @Autowired
    private ShipmentSnapshotMapper shipmentMapper;

    @Test
    void closedLoopPerceiveDecideActWriteback() throws Exception {
        String token = token();

        JsonNode overview = get(token, "/api/tower/overview");
        assertTrue(overview.path("kpi").has("otif30d"));
        assertTrue(overview.path("systems").size() >= 11);
        assertTrue(overview.path("ecosystem").has("SAP"));

        JsonNode stats = get(token, "/api/alert/stats");
        assertTrue(stats.path("high").asInt() > 0);
        assertTrue(stats.path("today").asInt() > 0);
        assertTrue(stats.path("open").asInt() > 0);

        JsonNode stuckPage = get(token, "/api/trace/page?stuck=true&size=20");
        assertTrue(stuckPage.path("records").size() > 0, "卡单筛选应返回记录而不是空页");
        assertTrue(stuckPage.path("total").asInt() > 0);

        seedHoldOrder();
        post(token, "/api/action",
                "{\"type\":\"OMS_HOLD\",\"targetKey\":\"SO-FLOW-HOLD\"}");
        assertEquals("HOLD", get(token, "/api/trace/SO-FLOW-HOLD").path("oms").path("status").asText());
        post(token, "/api/action",
                "{\"type\":\"OMS_UNHOLD\",\"targetKey\":\"SO-FLOW-HOLD\"}");
        assertEquals("CREATED", get(token, "/api/trace/SO-FLOW-HOLD").path("oms").path("status").asText());

        post(token, "/api/alert/evaluate", null);
        seedStuckOrder();
        post(token, "/api/alert/evaluate", null);
        JsonNode stuck = firstOpen(token, "ORDER_STUCK", "SO-FLOW-STUCK");
        JsonNode stuckAction = post(token, "/api/alert/" + stuck.path("id").asLong() + "/execute-suggested", null);
        assertEquals("SUCCESS", stuckAction.path("status").asText());
        JsonNode stuckOrder = get(token, "/api/trace/SO-FLOW-STUCK");
        assertEquals(10, stuckOrder.path("oms").path("priority").asInt());
        JsonNode stuckAfter = get(token, "/api/alert/page?status=OPEN&size=50");
        assertEquals(0, countOpen(stuckAfter, stuck.path("ruleCode").asText(), stuck.path("targetKey").asText()));

        seedDelay();
        post(token, "/api/alert/evaluate", null);
        JsonNode delay = firstOpen(token, "TMS_DELAY", "WB-FLOW-DELAY");
        JsonNode delayAction = post(token, "/api/alert/" + delay.path("id").asLong() + "/execute-suggested", null);
        assertEquals("SUCCESS", delayAction.path("status").asText());
        JsonNode waybill = get(token, "/api/trace/SO-FLOW-DELAY");
        assertEquals("IN_TRANSIT", waybill.path("tms").path("status").asText());
        assertEquals(false, waybill.path("tms").path("exceptionFlag").asBoolean());

        seedLowStock();
        post(token, "/api/alert/evaluate", null);
        JsonNode low = firstOpen(token, "LOW_STOCK", "SKU-FLOW-WH/WH-GZ");
        JsonNode lowAction = post(token, "/api/alert/" + low.path("id").asLong() + "/execute-suggested", null);
        assertEquals("SUCCESS", lowAction.path("status").asText());
        assertEquals("SRM_PURCHASE_SUGGEST", lowAction.path("type").asText());
        JsonNode snapshots = get(token, "/api/integration/snapshot/page?systemCode=SRM&dataType=PO&size=50");
        assertTrue(hasBizKey(snapshots, "IR-PO-SRM-SKU-FLOW-WH-WH-GZ"));
        JsonNode gz = get(token, "/api/forecast/replenish?warehouseCode=WH-GZ&sku=SKU-FLOW-WH&horizon=14&serviceDays=3");
        assertTrue(gz.get(0).path("inTransit").decimalValue().signum() > 0);
        JsonNode bj = get(token, "/api/forecast/replenish?warehouseCode=WH-BJ&sku=SKU-FLOW-WH&horizon=14&serviceDays=3");
        if (bj.isArray() && bj.size() > 0) {
            assertEquals(0, bj.get(0).path("inTransit").decimalValue().signum());
        }

        JsonNode sh = get(token, "/api/forecast/replenish?warehouseCode=WH-SH&sku=SKU002&horizon=14&serviceDays=3");
        assertEquals(0, new BigDecimal("12").compareTo(sh.get(0).path("inTransit").decimalValue()));
        JsonNode sku002Bj = get(token,
                "/api/forecast/replenish?warehouseCode=WH-BJ&sku=SKU002&horizon=14&serviceDays=3");
        assertEquals(0, sku002Bj.get(0).path("inTransit").decimalValue().signum());

        JsonNode sapPo = get(token, "/api/integration/snapshot/page?systemCode=SAP&dataType=PO&size=50");
        if (!hasBizKey(sapPo, "IR-PO-SAP-MAT-1000-WH-SH") && !hasBizKey(sapPo, "IR-PO-SAP-MAT-1000")) {
            JsonNode sap = firstOpenOrNull(token, "EXT_STATUS", null, "SAP_LOW_STOCK");
            if (sap != null) {
                JsonNode sapAction = post(token, "/api/alert/" + sap.path("id").asLong() + "/execute-suggested", null);
                assertEquals("SUCCESS", sapAction.path("status").asText());
                assertEquals("SAP_CREATE_PR", sapAction.path("type").asText());
            } else {
                post(token, "/api/action",
                        "{\"type\":\"SAP_CREATE_PR\",\"targetKey\":\"MAT-1000\",\"params\":{\"qty\":10,\"warehouseCode\":\"WH-SH\"}}");
            }
            sapPo = get(token, "/api/integration/snapshot/page?systemCode=SAP&dataType=PO&size=50");
        }
        assertTrue(hasBizKey(sapPo, "IR-PO-SAP-MAT-1000-WH-SH") || hasBizKey(sapPo, "IR-PO-SAP-MAT-1000"));

        JsonNode overrun = firstOpenOrNull(token, "COST_OVERRUN");
        if (overrun != null) {
            JsonNode overrunAction = post(token,
                    "/api/alert/" + overrun.path("id").asLong() + "/execute-suggested", null);
            assertEquals("SUCCESS", overrunAction.path("status").asText());
            assertEquals("TMS_SWITCH_CARRIER", overrunAction.path("type").asText());
        } else {
            post(token, "/api/action",
                    "{\"type\":\"TMS_SWITCH_CARRIER\",\"targetKey\":\"WB-FLOW-DELAY\",\"params\":{\"carrierCode\":\"JD\"}}");
        }
        JsonNode saving = get(token, "/api/cost/saving");
        assertTrue(saving.path("total").decimalValue().signum() >= 0);

        post(token, "/api/alert/evaluate", null);
        JsonNode still = get(token, "/api/alert/page?status=OPEN&size=100");
        assertEquals(0, countOpen(still, low.path("ruleCode").asText(), low.path("targetKey").asText()));
        assertEquals(0, countOpen(still, delay.path("ruleCode").asText(), delay.path("targetKey").asText()));

        JsonNode systems = get(token, "/api/integration/system");
        assertTrue(systems.size() >= 11);
        post(token, "/api/integration/sync/SAP", null);

        JsonNode auto = post(token, "/api/sandbox/auto/run", null);
        assertTrue(auto.path("recommended").path("id").asLong() > 0);
        JsonNode latest = get(token, "/api/sandbox/auto/latest");
        assertEquals(auto.path("recommended").path("id").asLong(),
                latest.path("recommended").path("id").asLong());
        JsonNode pending = post(token,
                "/api/sandbox/scenario/" + auto.path("recommended").path("id").asLong() + "/apply?execute=false",
                null);
        assertTrue(pending.isArray());

        JsonNode cost = get(token, "/api/cost/summary?days=30");
        assertTrue(cost.path("total").isNumber() || cost.path("byType").size() > 0);
        JsonNode rec = get(token, "/api/tower/overview");
        assertEquals(auto.path("recommended").path("id").asLong(),
                rec.path("recommendation").path("id").asLong());

        JsonNode capital = post(token, "/api/sandbox/capital",
                "{\"workingCapital\":100000000}");
        assertEquals("RELIABLE", capital.path("verdict").asText());
        assertTrue(capital.path("reliable").asBoolean());
        assertEquals(0, new BigDecimal("100000000").compareTo(capital.path("workingCapital").decimalValue()));
        assertTrue(capital.path("baseline").path("capitalUtilization").decimalValue()
                .compareTo(new BigDecimal("0.20")) < 0);
        assertEquals("RELIABLE", capital.path("baseline").path("capitalVerdict").asText());
        assertTrue(capital.path("headroom").decimalValue().compareTo(new BigDecimal("20")) > 0);
        assertTrue(capital.path("minReliableCapital").decimalValue()
                .compareTo(new BigDecimal("1000000")) < 0);
        assertTrue(capital.path("maxReliableDemandMultiplier").asInt() >= 5);
        assertTrue(capital.path("optimizations").size() > 0);
        assertEquals("RELIABLE", capital.path("demand5x").path("capitalVerdict").asText());
        assertTrue(capital.path("recommended").path("replenishLeadDays").asInt() <= 1);
        assertTrue(capital.path("recommended").path("name").asText().contains("短交期"));
        assertTrue(capital.path("recommended").path("recommended").asBoolean());
        assertTrue(capital.path("recommended").path("serviceLevel").decimalValue()
                .compareTo(new BigDecimal("0.995")) >= 0);
        assertEquals(0, capital.path("recommended").path("stockoutUnits").decimalValue().signum());
        assertTrue(capital.path("playbook").size() >= 5);

        JsonNode adopted = post(token, "/api/sandbox/capital/adopt",
                "{\"workingCapital\":100000000}");
        assertTrue(adopted.path("name").asText().contains("短交期"));
        assertEquals("MANUAL", adopted.path("kind").asText());
        assertTrue(adopted.path("id").asLong() > 0);
    }

    private void seedStuckOrder() {
        if (orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
                .eq(OrderSnapshot::getOrderNo, "SO-FLOW-STUCK")) > 0) {
            return;
        }
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("SO-FLOW-STUCK");
        order.setWarehouseCode("WH-SH");
        order.setStatus("AUDITED");
        order.setPriority(0);
        order.setOrderTime(LocalDateTime.now().minusHours(8));
        order.setPayAmount(new BigDecimal("66"));
        order.setQty(BigDecimal.ONE);
        orderMapper.insert(order);
    }

    private void seedHoldOrder() {
        if (orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
                .eq(OrderSnapshot::getOrderNo, "SO-FLOW-HOLD")) > 0) {
            return;
        }
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("SO-FLOW-HOLD");
        order.setWarehouseCode("WH-SH");
        order.setStatus("AUDITED");
        order.setPriority(0);
        order.setOrderTime(LocalDateTime.now().minusHours(2));
        order.setPayAmount(new BigDecimal("88"));
        order.setQty(BigDecimal.ONE);
        orderMapper.insert(order);
    }

    private void seedDelay() {
        if (shipmentMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShipmentSnapshot>()
                .eq(ShipmentSnapshot::getWaybillCode, "WB-FLOW-DELAY")) > 0) {
            return;
        }
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("SO-FLOW-DELAY");
        order.setWarehouseCode("WH-SH");
        order.setStatus("SHIPPED");
        order.setOrderTime(LocalDateTime.now().minusDays(2));
        order.setPayAmount(new BigDecimal("120"));
        order.setQty(BigDecimal.ONE);
        order.setTmsOrderNo("WB-FLOW-DELAY");
        orderMapper.insert(order);
        ShipmentSnapshot shipment = new ShipmentSnapshot();
        shipment.setWaybillCode("WB-FLOW-DELAY");
        shipment.setSourceNo("SO-FLOW-DELAY");
        shipment.setCarrierCode("SF");
        shipment.setStatus("IN_TRANSIT");
        shipment.setFromSiteCode("WH01");
        shipment.setPlannedArriveTime(LocalDateTime.now().minusHours(8));
        shipment.setFreightAmount(new BigDecimal("40"));
        shipment.setExceptionFlag(true);
        shipmentMapper.insert(shipment);
    }

    private void seedLowStock() {
        if (inventoryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSku, "SKU-FLOW-WH")
                .eq(InventorySnapshot::getWarehouseCode, "WH-GZ")) > 0) {
            return;
        }
        InventorySnapshot item = new InventorySnapshot();
        item.setSourceSystem("WMS");
        item.setWarehouseCode("WH-GZ");
        item.setSku("SKU-FLOW-WH");
        item.setQtyOnHand(new BigDecimal("2"));
        item.setQtyReserved(BigDecimal.ZERO);
        item.setQtyAvailable(new BigDecimal("2"));
        item.setSafetyQty(new BigDecimal("40"));
        inventoryMapper.insert(item);
    }

    private String token() throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("token").asText();
    }

    private JsonNode get(String token, String url) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode post(String token, String url, String json) throws Exception {
        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode firstOpenOrNull(String token, String type) throws Exception {
        return firstOpenOrNull(token, type, null, null);
    }

    private JsonNode firstOpenOrNull(String token, String type, String targetKey, String ruleCode) throws Exception {
        JsonNode page = get(token, "/api/alert/page?status=OPEN&type=" + type + "&size=100");
        for (JsonNode row : page.path("records")) {
            if (targetKey != null && !targetKey.equals(row.path("targetKey").asText())) {
                continue;
            }
            if (ruleCode != null && !ruleCode.equals(row.path("ruleCode").asText())) {
                continue;
            }
            return row;
        }
        return null;
    }

    private JsonNode firstOpen(String token, String type, String targetKey) throws Exception {
        return firstOpen(token, type, targetKey, null);
    }

    private JsonNode firstOpen(String token, String type, String targetKey, String ruleCode) throws Exception {
        JsonNode page = get(token, "/api/alert/page?status=OPEN&type=" + type + "&size=100");
        for (JsonNode row : page.path("records")) {
            if (targetKey != null && !targetKey.equals(row.path("targetKey").asText())) {
                continue;
            }
            if (ruleCode != null && !ruleCode.equals(row.path("ruleCode").asText())) {
                continue;
            }
            return row;
        }
        throw new AssertionError("没有 OPEN 预警 type=" + type + " key=" + targetKey + " rule=" + ruleCode);
    }

    private int countOpen(JsonNode page, String ruleCode, String targetKey) {
        int count = 0;
        for (JsonNode row : page.path("records")) {
            if (ruleCode.equals(row.path("ruleCode").asText())
                    && targetKey.equals(row.path("targetKey").asText())
                    && "OPEN".equals(row.path("status").asText())) {
                count++;
            }
        }
        return count;
    }

    private boolean hasBizKey(JsonNode page, String bizKey) {
        for (JsonNode row : page.path("records")) {
            if (bizKey.equals(row.path("bizKey").asText())) {
                return true;
            }
        }
        return false;
    }
}
