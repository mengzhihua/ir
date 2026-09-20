package com.ir.sandbox.engine;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectionTest {
    @Test
    void smallCatalogNeverProjectsStress() {
        SandboxEngine.Result normal = reliable(new BigDecimal("1000"));
        assertFalse(CapitalProjection.canProjectStress(
                normal, 200, new BigDecimal("1000000000")));
    }

    @Test
    void largeReliableWithHeadroomProjects() {
        SandboxEngine.Result normal = reliable(new BigDecimal("1518000"));
        assertTrue(CapitalProjection.canProjectStress(
                normal, 100_000, new BigDecimal("1000000000")));
        SandboxEngine.Result surge = CapitalProjection.scaleDemand(
                normal, BigDecimal.valueOf(5), new BigDecimal("1000000000"));
        assertEquals("RELIABLE", surge.getCapitalVerdict());
        assertEquals(0, new BigDecimal("7590000").compareTo(surge.getCashUsed()));
        assertEquals(0, normal.getStockoutUnits().compareTo(surge.getStockoutUnits()));
    }

    @Test
    void stockoutOrTightCashBlocksProjection() {
        SandboxEngine.Result stockout = reliable(new BigDecimal("1000"));
        stockout.setStockoutUnits(BigDecimal.ONE);
        assertFalse(CapitalProjection.canProjectStress(
                stockout, 100_000, new BigDecimal("1000000000")));
        SandboxEngine.Result expensive = reliable(new BigDecimal("200000000"));
        assertFalse(CapitalProjection.canProjectStress(
                expensive, 100_000, new BigDecimal("1000000000")));
    }

    @Test
    void scaledCashCanTurnTight() {
        SandboxEngine.Result normal = reliable(new BigDecimal("100"));
        SandboxEngine.Result tight = CapitalProjection.scaleDemand(
                normal, BigDecimal.valueOf(5), new BigDecimal("500"));
        assertEquals("TIGHT", tight.getCapitalVerdict());
        assertEquals(0, new BigDecimal("500").compareTo(tight.getCashUsed()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tight.getCashRemaining()));
    }

    private SandboxEngine.Result reliable(BigDecimal cashUsed) {
        SandboxEngine.Result result = new SandboxEngine.Result();
        result.setCapitalVerdict("RELIABLE");
        result.setCashUsed(cashUsed);
        result.setPurchaseCash(cashUsed);
        result.setOpsCash(BigDecimal.ZERO);
        result.setTotalCost(cashUsed);
        result.setStockoutUnits(BigDecimal.ZERO);
        result.setDeferredPurchaseQty(BigDecimal.ZERO);
        result.setCapitalShortage(BigDecimal.ZERO);
        result.setServiceLevel(BigDecimal.ONE);
        result.setSkuCount(100_000);
        return result;
    }
}
