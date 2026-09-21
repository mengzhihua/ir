package com.ir.alert.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.action.service.ActionService;
import com.ir.alert.entity.CtAlert;
import com.ir.alert.entity.CtRule;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.alert.mapper.CtRuleMapper;
import com.ir.forecast.service.ForecastService;
import com.ir.sandbox.service.BalanceAdvisor;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:alertengine;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AlertEngineTest {
    @Autowired
    private AlertEngine alertEngine;
    @Autowired
    private ActionService actions;
    @Autowired
    private CtAlertMapper alertMapper;
    @Autowired
    private CtActionMapper actionMapper;
    @Autowired
    private OrderSnapshotMapper orderMapper;
    @Autowired
    private WmsOrderSnapshotMapper wmsMapper;
    @Autowired
    private ForecastService forecasts;
    @Autowired
    private CtRuleMapper ruleMapper;
    @Autowired
    private InventorySnapshotMapper inventoryMapper;
    @Autowired
    private ShipmentSnapshotMapper shipmentMapper;
    @Autowired
    private com.ir.sandbox.service.BalancePolicy policy;

    @Test
    void stuckOrderRuleFiresAndIsIdempotent() {
        int first = alertEngine.evaluate().size();
        int second = alertEngine.evaluate().size();
        assertTrue(first > 0);
        assertEquals(first, second);
        assertTrue(alertEngine.openCount() > 0);
        assertTrue(alertEngine.openCount() <= first);
        assertTrue(alertEngine.openCount("FORECAST_STOCKOUT") <= alertEngine.openCount());
        Set<String> rules = new HashSet<>();
        alertMapper.selectList(null).forEach(alert ->
                rules.add(alert.getRuleCode()));
        assertTrue(rules.contains("OMS_STUCK"));
        assertTrue(rules.contains("WMS_STUCK"));
        assertTrue(rules.contains("TMS_DELAY"));
        assertTrue(rules.contains("LOW_STOCK"));
        assertTrue(rules.contains("COST_OVERRUN"));
        assertTrue(rules.contains("FORECAST_STOCKOUT"));
        assertTrue(rules.contains("UNSHIPPED_ORDER"));
        assertTrue(rules.contains("EXCEPTION_SHIPMENT"));
        assertTrue(rules.contains("SAP_LOW_STOCK"));
        assertTrue(rules.contains("SRM_PR_DRAFT"));
        assertTrue(rules.contains("DMS_PART_SHORTAGE"));
        assertTrue(rules.contains("CRM_OPEN_CASE"));
        assertTrue(rules.contains("OA_WF_PENDING"));
        assertTrue(rules.contains("ASN_DELAY"));
        assertTrue(rules.contains("INV_REQUEST_DRAFT"));
        assertTrue(rules.contains("INV_INPUT_UNVERIFIED"));
        assertTrue(rules.contains("BOM_ECN_DRAFT"));
        assertTrue(rules.contains("SAP_PR_OPEN"));
        assertTrue(rules.contains("SAP_MO_OPEN"));
        assertTrue(rules.contains("CRM_STALE_OPP"));
        assertTrue(rules.contains("SUPPLIER_RISK"));
        assertTrue(rules.contains("TMS_OPEN_DISPATCH"));
        CtAlert oa = alertMapper.selectList(null).stream()
                .filter(a -> "OA_WF_PENDING".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(oa);
        assertEquals("OA_APPROVE_TASK", oa.getSuggestedAction());
    }

    @Test
    void sapLowStockSuggestedFansOutPurchaseChain() {
        alertEngine.evaluate();
        CtAlert sap = alertMapper.selectList(null).stream()
                .filter(a -> "SAP_LOW_STOCK".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(sap);
        CtAction primary = alertEngine.executeSuggested(sap.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(primary);
        assertEquals("SAP_CREATE_PR", primary.getType());
        assertEquals("MAT-1000", primary.getTargetKey());
        assertEquals("SUCCESS", primary.getStatus());
        Set<String> types = new HashSet<>();
        actionMapper.selectList(null).forEach(row -> {
            if (sap.getId().equals(row.getAlertId())) {
                types.add(row.getType());
            }
        });
        assertTrue(types.contains("SAP_CREATE_PR"));
        assertTrue(types.contains("SRM_PURCHASE_SUGGEST"));
        assertTrue(types.contains("OA_START_WORKFLOW"));
        assertTrue(types.contains("WMS_REPLENISH"));
        assertResolved(sap);
    }

    @Test
    void costOverrunExecutesSwitchOnWaybillNotWarehouse() {
        alertEngine.evaluate();
        CtAlert overrun = alertMapper.selectList(null).stream()
                .filter(a -> "COST_OVERRUN".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(overrun);
        CtAction action = alertEngine.executeSuggested(overrun.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(action);
        assertEquals("TMS_SWITCH_CARRIER", action.getType());
        org.junit.jupiter.api.Assertions.assertTrue(
                action.getTargetKey() != null && action.getTargetKey().startsWith("WB"));
        org.junit.jupiter.api.Assertions.assertFalse("WH-SH".equals(action.getTargetKey()));
        org.junit.jupiter.api.Assertions.assertTrue(
                action.getParamsJson().contains("JD")
                        || action.getParamsJson().contains("SELF01")
                        || action.getParamsJson().contains("SF"));
        assertEquals("SUCCESS", action.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(action.getExpectedSaving());
        org.junit.jupiter.api.Assertions.assertTrue(
                action.getExpectedSaving().compareTo(java.math.BigDecimal.ZERO) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(
                action.getParamsJson() != null && action.getParamsJson().contains("actualSaving"));
        assertResolved(overrun);
    }

    @Test
    void delaySuggestedActionStaysOnWaybill() {
        alertEngine.evaluate();
        CtAlert delay = alertMapper.selectList(null).stream()
                .filter(a -> "TMS_DELAY".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(delay);
        assertEquals("WAYBILL", delay.getTargetType());
        org.junit.jupiter.api.Assertions.assertTrue(
                "TMS_SYNC_TRACK".equals(delay.getSuggestedAction())
                        || "TMS_SWITCH_CARRIER".equals(delay.getSuggestedAction()));
        CtAction action = alertEngine.executeSuggested(delay.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(action);
        assertEquals(delay.getTargetKey(), action.getTargetKey());
        assertEquals("SUCCESS", action.getStatus());
        if (BalanceAdvisor.SWITCH.equals(action.getType())) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    action.getExpectedSaving() != null
                            && action.getExpectedSaving().compareTo(java.math.BigDecimal.ZERO) > 0);
        } else {
            org.junit.jupiter.api.Assertions.assertTrue(
                    action.getExpectedSaving() == null
                            || action.getExpectedSaving().compareTo(java.math.BigDecimal.ZERO) == 0);
        }
        assertResolved(delay);
    }

    @Test
    void stuckOrderSuggestsPrioritizeWhenBalanced() {
        alertEngine.evaluate();
        CtAlert stuck = alertMapper.selectList(null).stream()
                .filter(a -> "OMS_STUCK".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(stuck);
        assertEquals("OMS_PRIORITIZE", stuck.getSuggestedAction());
        CtAction action = alertEngine.executeSuggested(stuck.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(action);
        assertEquals("OMS_PRIORITIZE", action.getType());
        assertEquals("SUCCESS", action.getStatus());
        OrderSnapshot order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
        org.junit.jupiter.api.Assertions.assertNotNull(order);
        assertEquals(Integer.valueOf(10), order.getPriority());
        assertResolved(stuck);
    }

    @Test
    void wmsStuckSuggestsAllocateWhenBalanced() {
        alertEngine.evaluate();
        CtAlert stuck = alertMapper.selectList(null).stream()
                .filter(a -> "WMS_STUCK".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(stuck);
        assertEquals("WMS_ALLOCATE", stuck.getSuggestedAction());
    }

    @Test
    void lowStockBalancedFansOutPurchaseAndReplenish() {
        alertEngine.evaluate();
        CtAlert low = alertMapper.selectList(null).stream()
                .filter(a -> "LOW_STOCK".equals(a.getRuleCode()))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(low);
        CtAction primary = alertEngine.executeSuggested(low.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(primary);
        assertEquals("SRM_PURCHASE_SUGGEST", primary.getType());
        Set<String> types = new HashSet<>();
        actionMapper.selectList(null).forEach(row -> {
            if (low.getId().equals(row.getAlertId())) {
                types.add(row.getType());
            }
        });
        assertTrue(types.contains("SRM_PURCHASE_SUGGEST"));
        assertTrue(types.contains("WMS_REPLENISH"));
        assertResolved(low);
        String sku = low.getTargetKey() != null && low.getTargetKey().contains("/")
                ? low.getTargetKey().split("/")[0] : low.getTargetKey();
        assertTrue(forecasts.inboundBySku().getOrDefault(sku,
                java.math.BigDecimal.ZERO).signum() > 0);
        alertEngine.evaluate();
        long stillOpen = alertMapper.selectList(null).stream()
                .filter(a -> "LOW_STOCK".equals(a.getRuleCode())
                        && low.getTargetKey().equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, stillOpen);
    }

    @Test
    void stockoutKeepsOpenWhenReplenishFails() {
        inventoryMapper.insert(stock("SKU-BATCH-FAIL", "WH-FAIL", "2"));
        alertEngine.evaluate();
        CtAlert low = alertMapper.selectList(null).stream()
                .filter(a -> "LOW_STOCK".equals(a.getRuleCode())
                        && a.getTargetKey() != null
                        && a.getTargetKey().startsWith("SKU-BATCH-FAIL")
                        && "OPEN".equals(a.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(low);
        CtAction primary = alertEngine.executeSuggested(low.getId());
        assertNotNull(primary);
        assertEquals("SRM_PURCHASE_SUGGEST", primary.getType());
        assertEquals("SUCCESS", primary.getStatus());
        boolean replenishFailed = actionMapper.selectList(null).stream()
                .anyMatch(row -> low.getId().equals(row.getAlertId())
                        && "WMS_REPLENISH".equals(row.getType())
                        && "FAILED".equals(row.getStatus()));
        assertTrue(replenishFailed);
        CtAlert afterExecute = alertMapper.selectById(low.getId());
        assertEquals("OPEN", afterExecute.getStatus());
        assertNull(afterExecute.getActionId());
        alertEngine.evaluate();
        CtAlert afterEvaluate = alertMapper.selectById(low.getId());
        assertNull(afterEvaluate.getActionId());
        assertTrue("OPEN".equals(afterEvaluate.getStatus())
                || "RESOLVED".equals(afterEvaluate.getStatus()));
    }

    @Test
    void wmsAllocateResolvesStuckAndEvaluateDoesNotReopen() {
        alertEngine.evaluate();
        CtAlert stuck = alertMapper.selectList(null).stream()
                .filter(a -> "WMS_STUCK".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(stuck);
        CtAction action = alertEngine.executeSuggested(stuck.getId());
        assertNotNull(action);
        assertEquals("WMS_ALLOCATE", action.getType());
        assertEquals("SUCCESS", action.getStatus());
        assertResolved(stuck);
        WmsOrderSnapshot outbound = wmsMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsOrderSnapshot>()
                        .eq(WmsOrderSnapshot::getCode, action.getTargetKey()));
        assertNotNull(outbound);
        assertEquals("ALLOCATED", outbound.getStatus());
        alertEngine.evaluate();
        long stillOpen = alertMapper.selectList(null).stream()
                .filter(a -> "WMS_STUCK".equals(a.getRuleCode())
                        && stuck.getTargetKey().equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, stillOpen);
    }

    @Test
    void evaluateResolvesStuckAlertWhenOrderLeavesAudited() {
        alertEngine.evaluate();
        CtAlert stuck = alertMapper.selectList(null).stream()
                .filter(a -> "OMS_STUCK".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(stuck);
        OrderSnapshot order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, stuck.getTargetKey()));
        assertNotNull(order);
        order.setStatus("ALLOCATED");
        orderMapper.updateById(order);
        alertEngine.evaluate();
        assertResolved(stuck);
        long stillOpen = alertMapper.selectList(null).stream()
                .filter(a -> "OMS_STUCK".equals(a.getRuleCode())
                        && stuck.getTargetKey().equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, stillOpen);
    }

    @Test
    void prioritizeExecuteDoesNotReopenWhileConditionRemains() {
        OrderSnapshot seeded = new OrderSnapshot();
        seeded.setOrderNo("SO-ALERT-SUPPRESS");
        seeded.setWarehouseCode("WH-SH");
        seeded.setStatus("AUDITED");
        seeded.setPriority(1);
        seeded.setOrderTime(LocalDateTime.now().minusHours(8));
        seeded.setPayAmount(new BigDecimal("99"));
        seeded.setQty(BigDecimal.ONE);
        orderMapper.insert(seeded);
        alertEngine.evaluate();
        CtAlert stuck = alertMapper.selectList(null).stream()
                .filter(a -> "OMS_STUCK".equals(a.getRuleCode())
                        && "SO-ALERT-SUPPRESS".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(stuck);
        CtAction action = alertEngine.executeSuggested(stuck.getId());
        assertNotNull(action);
        assertEquals("SUCCESS", action.getStatus());
        assertResolved(stuck);
        alertEngine.evaluate();
        long reopened = alertMapper.selectList(null).stream()
                .filter(a -> "OMS_STUCK".equals(a.getRuleCode())
                        && stuck.getTargetKey().equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, reopened);
        CtAlert handled = alertMapper.selectById(stuck.getId());
        assertEquals(action.getId(), handled.getActionId());
        OrderSnapshot order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, stuck.getTargetKey()));
        assertNotNull(order);
        order.setStatus("ALLOCATED");
        orderMapper.updateById(order);
        alertEngine.evaluate();
        CtAlert cleared = alertMapper.selectById(stuck.getId());
        assertEquals("RESOLVED", cleared.getStatus());
        assertNull(cleared.getActionId());
    }

    @Test
    void shInboundDoesNotCoverBjLowStock() {
        InventorySnapshot sh = stock("SKU-INB-WH", "WH-SH", "2");
        InventorySnapshot bj = stock("SKU-INB-WH", "WH-BJ", "2");
        inventoryMapper.insert(sh);
        inventoryMapper.insert(bj);
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("type", "SRM_PURCHASE_SUGGEST");
        request.put("targetKey", "SKU-INB-WH");
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("sku", "SKU-INB-WH");
        params.put("qty", 50);
        params.put("warehouseCode", "WH-SH");
        request.put("params", params);
        assertEquals("SUCCESS", actions.createAndExecute(request).getStatus());
        alertEngine.evaluate();
        long shOpen = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-INB-WH/WH-SH".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        long bjOpen = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-INB-WH/WH-BJ".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, shOpen);
        assertEquals(1, bjOpen);
    }

    @Test
    void legacyLowStockKeyStillSuppresses() {
        InventorySnapshot item = stock("SKU-LEGACY-WH", "WH-SH", "2");
        inventoryMapper.insert(item);
        CtAlert handled = new CtAlert();
        handled.setAlertNo("ALT-LEGACY-WH");
        handled.setRuleCode("LOW_STOCK");
        handled.setType("LOW_STOCK");
        handled.setSeverity("MEDIUM");
        handled.setTargetType("SKU");
        handled.setTargetKey("SKU-LEGACY-WH");
        handled.setWarehouseCode("WH-SH");
        handled.setTitle("低库存");
        handled.setStatus("RESOLVED");
        handled.setActionId(1L);
        handled.setSuggestedAction("SRM_PURCHASE_SUGGEST");
        alertMapper.insert(handled);
        alertEngine.evaluate();
        long opened = alertMapper.selectList(null).stream()
                .filter(a -> a.getTargetKey() != null
                        && a.getTargetKey().startsWith("SKU-LEGACY-WH")
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, opened);
        CtAlert still = alertMapper.selectById(handled.getId());
        assertEquals("RESOLVED", still.getStatus());
        assertEquals("SKU-LEGACY-WH/WH-SH", still.getTargetKey());
        assertEquals("SKU_WAREHOUSE", still.getTargetType());
        assertEquals(Long.valueOf(1L), still.getActionId());
    }

    @Test
    void legacyOpenLowStockKeyMigratesToWarehouse() {
        InventorySnapshot item = stock("SKU-MIG-WH", "WH-SH", "2");
        inventoryMapper.insert(item);
        CtAlert open = new CtAlert();
        open.setAlertNo("ALT-MIG-WH");
        open.setRuleCode("LOW_STOCK");
        open.setType("LOW_STOCK");
        open.setSeverity("MEDIUM");
        open.setTargetType("SKU");
        open.setTargetKey("SKU-MIG-WH");
        open.setWarehouseCode("WH-SH");
        open.setTitle("低库存");
        open.setStatus("OPEN");
        open.setSuggestedAction("SRM_PURCHASE_SUGGEST");
        alertMapper.insert(open);
        alertEngine.evaluate();
        CtAlert migrated = alertMapper.selectById(open.getId());
        assertEquals("SKU-MIG-WH/WH-SH", migrated.getTargetKey());
        assertEquals("SKU_WAREHOUSE", migrated.getTargetType());
        assertEquals("OPEN", migrated.getStatus());
        long duplicates = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-MIG-WH/WH-SH".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(1, duplicates);
    }

    @Test
    void lowStockTargetKeyIncludesWarehouse() {
        InventorySnapshot sh = stock("SKU-ALERT-WH", "WH-SH", "2");
        InventorySnapshot bj = stock("SKU-ALERT-WH", "WH-BJ", "1");
        inventoryMapper.insert(sh);
        inventoryMapper.insert(bj);
        alertEngine.evaluate();
        long keys = alertMapper.selectList(null).stream()
                .filter(a -> "LOW_STOCK".equals(a.getRuleCode())
                        && a.getTargetKey() != null
                        && a.getTargetKey().startsWith("SKU-ALERT-WH/"))
                .count();
        assertEquals(2, keys);
        CtAlert shAlert = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-ALERT-WH/WH-SH".equals(a.getTargetKey()))
                .findFirst().orElse(null);
        assertNotNull(shAlert);
        assertEquals("SKU_WAREHOUSE", shAlert.getTargetType());
        assertEquals("WH-SH", shAlert.getWarehouseCode());
        bj.setQtyAvailable(new BigDecimal("80"));
        inventoryMapper.updateById(bj);
        alertEngine.evaluate();
        long bjOpen = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-ALERT-WH/WH-BJ".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, bjOpen);
        long shOpen = alertMapper.selectList(null).stream()
                .filter(a -> "SKU-ALERT-WH/WH-SH".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(1, shOpen);
    }

    @Test
    void lowStockExecuteUsesRopQtyNotSnapshotSafety() throws Exception {
        alertEngine.evaluate();
        CtAlert low = null;
        BigDecimal suggest = null;
        BigDecimal demand = null;
        for (CtAlert alert : alertMapper.selectList(null)) {
            if (!"LOW_STOCK".equals(alert.getRuleCode())
                    || !"OPEN".equals(alert.getStatus())
                    || alert.getTargetKey() == null
                    || !alert.getTargetKey().contains("/")) {
                continue;
            }
            String[] parts = alert.getTargetKey().split("/", 2);
            java.util.List<java.util.Map<String, Object>> rows = forecasts.replenish(
                    parts[1], parts[0], 14, policy.safetyDays(), policy.replenishLeadDays());
            if (rows.isEmpty()) {
                continue;
            }
            BigDecimal d = (BigDecimal) rows.get(0).get("forecastDemand");
            BigDecimal s = (BigDecimal) rows.get(0).get("suggestQty");
            if (d.signum() > 0 && s.signum() > 0) {
                low = alert;
                demand = d;
                suggest = s;
                break;
            }
        }
        assertNotNull(low);
        CtAction primary = alertEngine.executeSuggested(low.getId());
        assertNotNull(primary);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> params = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(primary.getParamsJson(), java.util.Map.class);
        BigDecimal qty = new BigDecimal(String.valueOf(params.get("qty")));
        assertTrue(qty.compareTo(demand) <= 0);
        assertTrue(qty.compareTo(suggest.multiply(new BigDecimal("1.5"))
                .setScale(0, java.math.RoundingMode.UP)) <= 0);
    }

    @Test
    void disabledRuleLeavesExistingOpenAlerts() {
        ShipmentSnapshot late = new ShipmentSnapshot();
        late.setWaybillCode("WB-ALERT-KEEP");
        late.setSourceNo("SO-ALERT-KEEP");
        late.setCarrierCode("SF");
        late.setStatus("IN_TRANSIT");
        late.setFromSiteCode("WH01");
        late.setPlannedArriveTime(LocalDateTime.now().minusHours(10));
        late.setFreightAmount(new BigDecimal("25"));
        late.setExceptionFlag(false);
        shipmentMapper.insert(late);
        alertEngine.evaluate();
        CtAlert delay = alertMapper.selectList(null).stream()
                .filter(a -> "TMS_DELAY".equals(a.getRuleCode())
                        && "WB-ALERT-KEEP".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(delay);
        CtRule rule = ruleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CtRule>()
                        .eq(CtRule::getCode, "TMS_DELAY"));
        assertNotNull(rule);
        rule.setEnabled(false);
        ruleMapper.updateById(rule);
        try {
            alertEngine.evaluate();
            CtAlert still = alertMapper.selectById(delay.getId());
            assertEquals("OPEN", still.getStatus());
        } finally {
            rule.setEnabled(true);
            ruleMapper.updateById(rule);
        }
    }

    @Test
    void terminalShipmentsDoNotKeepDelayOrExceptionAlerts() {
        ShipmentSnapshot cancelled = new ShipmentSnapshot();
        cancelled.setWaybillCode("WB-ALERT-CANCEL");
        cancelled.setSourceNo("SO-ALERT-CANCEL");
        cancelled.setCarrierCode("SF");
        cancelled.setStatus("CANCELLED");
        cancelled.setFromSiteCode("WH01");
        cancelled.setPlannedArriveTime(LocalDateTime.now().minusHours(8));
        cancelled.setFreightAmount(new BigDecimal("30"));
        cancelled.setExceptionFlag(true);
        shipmentMapper.insert(cancelled);
        alertEngine.evaluate();
        long open = alertMapper.selectList(null).stream()
                .filter(a -> "WB-ALERT-CANCEL".equals(a.getTargetKey())
                        && "OPEN".equals(a.getStatus()))
                .count();
        assertEquals(0, open);
    }

    @Test
    void executeSuggestedRejectsReplay() {
        alertEngine.evaluate();
        CtAlert open = alertMapper.selectList(null).stream()
                .filter(a -> "OPEN".equals(a.getStatus())
                        && "OMS_STUCK".equals(a.getRuleCode())
                        && a.getSuggestedAction() != null
                        && !a.getSuggestedAction().trim().isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(open);
        assertNotNull(alertEngine.executeSuggested(open.getId()));
        assertEquals("RESOLVED", alertMapper.selectById(open.getId()).getStatus());
        org.junit.jupiter.api.Assertions.assertThrows(com.ir.common.BizException.class,
                () -> alertEngine.executeSuggested(open.getId()));
        assertEquals("RESOLVED", alertMapper.selectById(open.getId()).getStatus());
    }

    private InventorySnapshot stock(String sku, String warehouse, String available) {
        InventorySnapshot item = new InventorySnapshot();
        item.setSourceSystem("WMS");
        item.setWarehouseCode(warehouse);
        item.setSku(sku);
        item.setQtyOnHand(new BigDecimal(available));
        item.setQtyReserved(BigDecimal.ZERO);
        item.setQtyAvailable(new BigDecimal(available));
        item.setSafetyQty(new BigDecimal("40"));
        return item;
    }

    private void assertResolved(CtAlert before) {
        CtAlert after = alertMapper.selectById(before.getId());
        assertNotNull(after);
        assertEquals("RESOLVED", after.getStatus());
        assertNotNull(after.getResolvedAt());
    }
}
