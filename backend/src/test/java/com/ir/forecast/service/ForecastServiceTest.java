package com.ir.forecast.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.ir.action.entity.CtAction;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ForecastServiceTest {
    @Autowired
    private ForecastService forecasts;

    @Test
    void replenishUsesSrmInboundForSku002() {
        Map<String, BigDecimal> inbound = forecasts.inboundBySku();
        assertEquals(0, new BigDecimal("12").compareTo(inbound.get("SKU002")));
        assertEquals(0, new BigDecimal("20").compareTo(inbound.get("MAT-1000")));

        List<Map<String, Object>> rows = forecasts.replenish(null, "SKU002", 14, 3);
        assertFalse(rows.isEmpty());
        Map<String, Object> first = rows.get(0);
        assertEquals(0, new BigDecimal("12").compareTo((BigDecimal) first.get("inTransit")));
        assertEquals(first.get("suggestQty"), first.get("suggestedQty"));
    }

    @Test
    void toActionHonorsWmsReplenishType() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "WMS_REPLENISH");
        row.put("sku", "SKU001");
        row.put("warehouseCode", "WH-SH");
        row.put("suggestQty", 8);
        List<CtAction> actions = forecasts.toActions(Collections.singletonList(row));
        assertEquals(1, actions.size());
        assertEquals("WMS_REPLENISH", actions.get(0).getType());
        assertEquals("WH-SH", actions.get(0).getTargetKey());
        assertTrue("SUCCESS".equals(actions.get(0).getStatus())
                || "FAILED".equals(actions.get(0).getStatus()));
    }
}
