package com.ir.alert;

import com.ir.action.CtAction;
import com.ir.action.CtActionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
    }
}
