package com.ir.sandbox.engine;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalScaleTest {
    @Test
    void dailySeriesMatchesHorizonAndMetricsStaySane() {
        BaselineData data = ScaleCatalog.build(400, new BigDecimal("80"));
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(7);
        params.setWorkingCapital(new BigDecimal("100000"));
        SandboxEngine.Result result = new SandboxEngine().run(params, data);
        assertEquals(7, result.getDailySeries().size());
        assertEquals(400, result.getSkuCount());
        assertEquals(0, new BigDecimal("32000").compareTo(result.getInventoryUnits()));
        assertTrue(result.getServiceLevel().signum() >= 0);
        assertTrue(result.getServiceLevel().compareTo(BigDecimal.ONE) <= 0);
        assertTrue(result.getStockoutUnits().signum() >= 0);
        assertTrue(result.getCashUsed().signum() >= 0);
        assertTrue(result.getPerSkuSummary().size() <= CapitalTiers.MAX_SKU_SUMMARY);
        assertTrue(result.isSkuSummaryTruncated());
    }

    @Test
    void largerCapitalDoesNotWorsenVerdict() {
        BaselineData data = ScaleCatalog.build(120, new BigDecimal("50"));
        SandboxEngine engine = new SandboxEngine();
        String previous = null;
        for (BigDecimal amount : CapitalTiers.amounts()) {
            ScenarioParams params = new ScenarioParams();
            params.setHorizonDays(7);
            params.setWorkingCapital(amount);
            SandboxEngine.Result result = engine.run(params, data);
            assertEquals(7, result.getDailySeries().size());
            assertTrue(result.getCashUsed().signum() >= 0);
            assertTrue(result.getServiceLevel().compareTo(BigDecimal.ONE) <= 0);
            if ("RELIABLE".equals(previous)) {
                assertEquals("RELIABLE", result.getCapitalVerdict(),
                        "更大资金盘不能从可靠退回 " + amount);
            }
            previous = result.getCapitalVerdict();
        }
        ScenarioParams rich = new ScenarioParams();
        rich.setHorizonDays(7);
        rich.setWorkingCapital(new BigDecimal("1000000000"));
        assertEquals("RELIABLE", engine.run(rich, data).getCapitalVerdict());
    }

    @Test
    void twoThousandSkusRunWithoutBlowingSeries() {
        BaselineData data = ScaleCatalog.build(2000, new BigDecimal("20"));
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(3);
        params.setWorkingCapital(new BigDecimal("10000000"));
        SandboxEngine.Result result = new SandboxEngine().run(params, data);
        assertEquals(3, result.getDailySeries().size());
        assertEquals(2000, result.getSkuCount());
        assertNotNull(result.getCapitalVerdict());
        assertTrue(result.getCashUsed().signum() >= 0);
    }

    @Test
    void tenMillionQtyPerSkuStaysSane() {
        BaselineData data = ScaleCatalog.build(8, CapitalTiers.MAX_QTY);
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(3);
        params.setWorkingCapital(new BigDecimal("1000000000"));
        SandboxEngine.Result result = new SandboxEngine().run(params, data);
        assertEquals(8, result.getSkuCount());
        assertEquals(0, new BigDecimal("80000000").compareTo(result.getInventoryUnits()));
        assertEquals(3, result.getDailySeries().size());
        assertTrue(result.getCashUsed().signum() >= 0);
        assertTrue(result.getServiceLevel().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void oneHundredThousandSkusStayWithinCaps() {
        BaselineData data = ScaleCatalog.build(100_000, new BigDecimal("12"));
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(1);
        params.setWorkingCapital(new BigDecimal("1000000000"));
        long started = System.currentTimeMillis();
        SandboxEngine.Result result = new SandboxEngine().run(params, data);
        assertEquals(1, result.getDailySeries().size());
        assertEquals(100_000, result.getSkuCount());
        assertEquals(0, new BigDecimal("1200000").compareTo(result.getInventoryUnits()));
        assertTrue(result.getCashUsed().signum() >= 0);
        assertTrue(result.getServiceLevel().signum() >= 0);
        assertTrue(result.getServiceLevel().compareTo(BigDecimal.ONE) <= 0);
        assertTrue(result.getPerSkuSummary().size() <= CapitalTiers.MAX_SKU_SUMMARY);
        assertTrue(System.currentTimeMillis() - started < 20_000);
        assertTrue(result.getElapsedMs() > 0);
        assertTrue(result.getElapsedMs() < 20_000);
    }

    @Test
    void doesNotSpendCashStockingEmptyWarehouses() {
        BaselineData data = new BaselineData();
        com.ir.snapshot.entity.InventorySnapshot item = new com.ir.snapshot.entity.InventorySnapshot();
        item.setWarehouseCode("WH-SH");
        item.setSku("SKU001");
        item.setQtyAvailable(new BigDecimal("1000"));
        data.getInventory().add(item);
        data.getDemandBySku().put("SKU001", java.util.Collections.nCopies(7, BigDecimal.TEN));
        data.getSkuWarehouse().put("SKU001", "WH-SH");
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(7);
        params.setWorkingCapital(new BigDecimal("4000"));
        params.setSafetyDays(3);
        params.setPurchaseCostPerUnit(new BigDecimal("50"));
        SandboxEngine.Result result = new SandboxEngine().run(params, data);
        assertEquals("RELIABLE", result.getCapitalVerdict(),
                "只补主仓时 4000 资金应盖住运费，不应再给空仓铺货");
        assertEquals(0, result.getDeferredPurchaseQty().signum());
    }
}
