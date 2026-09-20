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
        assertEquals(0, new BigDecimal("12").compareTo(forecasts.inboundOf("SKU002", "WH-SH")));
        assertEquals(0, BigDecimal.ZERO.compareTo(forecasts.inboundOf("SKU002", "WH-BJ")));
        assertEquals(0, new BigDecimal("20").compareTo(forecasts.inboundOf("MAT-1000", "WH-SH")));

        List<Map<String, Object>> sh = forecasts.replenish("WH-SH", "SKU002", 14, 3);
        assertFalse(sh.isEmpty());
        Map<String, Object> first = sh.get(0);
        assertEquals(0, new BigDecimal("12").compareTo((BigDecimal) first.get("inTransit")));
        assertEquals(first.get("suggestQty"), first.get("suggestedQty"));
        assertEquals(3, first.get("replenishLeadDays"));
        assertEquals(6, first.get("coverDays"));
        assertTrue(first.get("orderByDate") != null);
        BigDecimal demand = (BigDecimal) first.get("forecastDemand");
        BigDecimal suggest = (BigDecimal) first.get("suggestQty");
        assertTrue(suggest.compareTo(demand) <= 0);

        List<Map<String, Object>> shortLead = forecasts.replenish("WH-SH", "SKU002", 14, 3, 1);
        List<Map<String, Object>> longLead = forecasts.replenish("WH-SH", "SKU002", 14, 3, 5);
        assertEquals(1, shortLead.get(0).get("replenishLeadDays"));
        assertEquals(4, shortLead.get(0).get("coverDays"));
        assertEquals(5, longLead.get(0).get("replenishLeadDays"));
        assertEquals(8, longLead.get(0).get("coverDays"));
        java.time.LocalDate shortDue = (java.time.LocalDate) shortLead.get(0).get("orderByDate");
        java.time.LocalDate longDue = (java.time.LocalDate) longLead.get(0).get("orderByDate");
        assertFalse(longDue.isAfter(shortDue));
        BigDecimal leanQty = (BigDecimal) shortLead.get(0).get("suggestQty");
        BigDecimal fatQty = (BigDecimal) longLead.get(0).get("suggestQty");
        assertTrue(leanQty.compareTo(fatQty) <= 0);

        List<Map<String, Object>> winner = forecasts.replenish("WH-SH", "SKU002", 14, 1, 1);
        assertEquals(2, winner.get(0).get("coverDays"));
        assertTrue(((BigDecimal) winner.get(0).get("suggestQty"))
                .compareTo((BigDecimal) first.get("suggestQty")) <= 0);

        List<Map<String, Object>> bj = forecasts.replenish("WH-BJ", "SKU002", 14, 3);
        assertFalse(bj.isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) bj.get(0).get("inTransit")));
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
