package com.ir.alert.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.alert.entity.CtAlert;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.sandbox.service.BalanceAdvisor;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AlertEngineTest {
    @Autowired
    private AlertEngine alertEngine;
    @Autowired
    private CtAlertMapper alertMapper;
    @Autowired
    private CtActionMapper actionMapper;
    @Autowired
    private OrderSnapshotMapper orderMapper;

    @Test
    void stuckOrderRuleFiresAndIsIdempotent() {
        int first = alertEngine.evaluate().size();
        int second = alertEngine.evaluate().size();
        assertTrue(first > 0);
        assertEquals(first, second);
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
    }
}
