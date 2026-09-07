package com.ir.alert;

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
    }
}
