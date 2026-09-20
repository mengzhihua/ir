package com.ir.sandbox.engine;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSandboxPickerTest {
    @Test
    void keepsCheapestWhenRobustIsMuchMoreExpensive() {
        Map<String, Object> cheap = row("现金最低", "1.000", "0", "1000", false);
        Map<String, Object> robust = row("稳健但贵", "1.000", "0", "1500", true);
        AutoSandboxPicker.Decision<Map<String, Object>> decision = AutoSandboxPicker.decide(
                Arrays.asList(cheap, robust),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")),
                row -> Boolean.TRUE.equals(row.get("stressReliable")),
                row -> String.valueOf(row.get("name")));
        assertEquals("现金最低", decision.recommended.get("name"));
        assertFalse(decision.usedRobust);
        assertEquals("稳健但贵", decision.robustBest.get("name"));
    }

    @Test
    void switchesToRobustWithinCashSlack() {
        Map<String, Object> cheap = row("现金最低", "1.000", "0", "1000", false);
        Map<String, Object> robust = row("稳健", "1.000", "0", "1100", true);
        AutoSandboxPicker.Decision<Map<String, Object>> decision = AutoSandboxPicker.decide(
                Arrays.asList(cheap, robust),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")),
                row -> Boolean.TRUE.equals(row.get("stressReliable")),
                row -> String.valueOf(row.get("name")));
        assertEquals("稳健", decision.recommended.get("name"));
        assertTrue(decision.usedRobust);
        assertTrue(String.valueOf(decision.rationale.get("reason")).contains("稳健"));
    }

    @Test
    void cheapestAlreadyRobustIsNotASwitch() {
        Map<String, Object> winner = row("低安全短交期", "1.000", "0", "10730", true);
        Map<String, Object> other = row("经济承运", "1.000", "0", "15441", false);
        AutoSandboxPicker.Decision<Map<String, Object>> decision = AutoSandboxPicker.decide(
                Arrays.asList(other, winner),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")),
                row -> Boolean.TRUE.equals(row.get("stressReliable")),
                row -> String.valueOf(row.get("name")));
        assertEquals("低安全短交期", decision.recommended.get("name"));
        assertFalse(decision.usedRobust);
        assertEquals(winner, decision.cashBest);
        assertEquals(winner, decision.robustBest);
    }

    @Test
    void ignoresStockoutEvenIfCheaper() {
        Map<String, Object> stockout = row("缺货", "0.99", "12", "100", true);
        Map<String, Object> ok = row("可行", "1.000", "0", "2000", false);
        AutoSandboxPicker.Decision<Map<String, Object>> decision = AutoSandboxPicker.decide(
                Arrays.asList(stockout, ok),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")),
                row -> Boolean.TRUE.equals(row.get("stressReliable")),
                row -> String.valueOf(row.get("name")));
        assertEquals("可行", decision.recommended.get("name"));
        assertEquals(1, decision.rationale.get("eligible"));
        assertEquals(1, decision.rationale.get("rejected"));
    }

    private Map<String, Object> row(String name, String sl, String stockout, String cash, boolean robust) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("serviceLevel", sl);
        row.put("stockoutUnits", stockout);
        row.put("cashUsed", cash);
        row.put("stressReliable", robust);
        return row;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
