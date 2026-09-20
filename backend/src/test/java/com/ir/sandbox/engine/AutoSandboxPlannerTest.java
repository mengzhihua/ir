package com.ir.sandbox.engine;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSandboxPlannerTest {
    @Test
    void gridHasTenBaseCandidates() {
        List<AutoSandboxPlanner.Candidate> plan = AutoSandboxPlanner.plan(
                Collections.emptyMap(), Collections.emptyList(), "BALANCED");
        assertEquals(10, plan.size());
        assertEquals("GRID", plan.get(0).source);
    }

    @Test
    void warehousesAndAlertsAddDerivedCandidates() {
        Map<String, Integer> alerts = new LinkedHashMap<String, Integer>();
        alerts.put("TMS_DELAY", 2);
        alerts.put("LOW_STOCK", 4);
        alerts.put("COST_OVERRUN", 1);
        alerts.put("ORDER_STUCK", 1);
        List<AutoSandboxPlanner.Candidate> plan = AutoSandboxPlanner.plan(
                alerts, Arrays.asList("WH-SH", "WH-BJ", "WH-GZ"), "COST");
        assertTrue(plan.size() > 10);
        assertTrue(plan.stream().anyMatch(row -> "WAREHOUSE".equals(row.source) && "WH-BJ".equals(row.signal)));
        assertTrue(plan.stream().anyMatch(row -> "ALERT".equals(row.source) && "TMS_DELAY".equals(row.signal)));
        assertTrue(plan.stream().anyMatch(row -> "ALERT".equals(row.source) && "LOW_STOCK".equals(row.signal)));
        assertTrue(plan.stream().anyMatch(row -> "STANCE".equals(row.source) && "COST".equals(row.signal)));
        assertTrue(plan.stream().noneMatch(row -> "WH-SH".equals(row.signal) && "WAREHOUSE".equals(row.source)));
    }

    @Test
    void signalsListOnlyOpenTypes() {
        Map<String, Integer> alerts = new LinkedHashMap<String, Integer>();
        alerts.put("LOW_STOCK", 3);
        alerts.put("TMS_DELAY", 0);
        List<String> signals = AutoSandboxPlanner.signalsOf(alerts);
        assertEquals(Collections.singletonList("LOW_STOCK"), signals);
    }
}
