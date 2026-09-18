package com.ir.sandbox.engine;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceFirstPickerTest {
    @Test
    void prefersLowestCashAmongFullService() {
        Map<String, Object> cheapSlow = row("经济承运", "1.0000", "0", "15441");
        Map<String, Object> winner = row("低安全短交期", "1.0000", "0", "10730");
        Map<String, Object> stockout = row("低安全库存", "0.9914", "10.22", "7870");
        Map<String, Object> picked = ServiceFirstPicker.pickByCash(
                Arrays.asList(cheapSlow, winner, stockout),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")));
        assertEquals("低安全短交期", picked.get("name"));
    }

    @Test
    void returnsNullWhenNobodyMeetsService() {
        Map<String, Object> picked = ServiceFirstPicker.pickByCash(
                Arrays.asList(row("缺货", "0.93", "12", "100")),
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")));
        assertNull(picked);
    }

    private Map<String, Object> row(String name, String sl, String stockout, String cash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("serviceLevel", sl);
        row.put("stockoutUnits", stockout);
        row.put("cashUsed", cash);
        return row;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
