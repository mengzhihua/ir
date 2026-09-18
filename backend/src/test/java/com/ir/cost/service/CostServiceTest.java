package com.ir.cost.service;

import com.ir.snapshot.entity.CostRecord;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostServiceTest {
    @Test
    void preferSettlementDropsTmsFreightOnlyForSettledOrders() {
        CostRecord tmsSettled = row("TMS", "FREIGHT", "100", "SO-1");
        CostRecord tmsOpen = row("TMS", "FREIGHT", "40", "SO-2");
        CostRecord bms = row("BMS", "FREIGHT", "80", "SO-1");
        CostRecord handling = row("BMS", "HANDLING", "10", "SO-1");
        List<CostRecord> preferred = CostService.preferSettlement(
                Arrays.asList(tmsSettled, tmsOpen, bms, handling));
        assertEquals(3, preferred.size());
        assertTrue(preferred.contains(bms));
        assertTrue(preferred.contains(tmsOpen));
        assertTrue(preferred.contains(handling));
        BigDecimal freight = BigDecimal.ZERO;
        for (CostRecord row : preferred) {
            if (CostService.freight(row)) {
                freight = freight.add(row.getAmount());
            }
        }
        assertEquals(0, new BigDecimal("120").compareTo(freight));
    }

    @Test
    void preferSettlementFallsBackToTmsWithoutBmsFreight() {
        CostRecord tms = row("TMS", "FREIGHT", "100", "SO-9");
        CostRecord handling = row("WMS", "HANDLING", "10", "SO-9");
        List<CostRecord> preferred = CostService.preferSettlement(
                Arrays.asList(tms, handling));
        assertEquals(2, preferred.size());
        assertTrue(preferred.contains(tms));
    }

    @Test
    void transportCountsAsFreight() {
        CostRecord transport = row("BMS", "TRANSPORT", "36", "SO-3");
        CostRecord tms = row("TMS", "FREIGHT", "40", "SO-3");
        List<CostRecord> preferred = CostService.preferSettlement(
                Arrays.asList(transport, tms));
        assertEquals(1, preferred.size());
        assertEquals("BMS", preferred.get(0).getSourceSystem());
        assertTrue(CostService.freightType("TRANSPORT"));
    }

    private static CostRecord row(String source, String type, String amount, String orderNo) {
        CostRecord row = new CostRecord();
        row.setSourceSystem(source);
        row.setCostType(type);
        row.setAmount(new BigDecimal(amount));
        row.setOrderNo(orderNo);
        return row;
    }
}
