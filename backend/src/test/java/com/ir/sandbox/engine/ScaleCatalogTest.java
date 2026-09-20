package com.ir.sandbox.engine;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleCatalogTest {
    @Test
    void buildsRequestedSkuCountAndQty() {
        BaselineData data = ScaleCatalog.build(80, new BigDecimal("250"));
        assertEquals(80, data.getDemandBySku().size());
        assertEquals(80, data.getInventory().size());
        assertEquals(0, new BigDecimal("250").compareTo(data.getInventory().get(0).getQtyAvailable()));
        assertEquals(14, data.getDemandBySku().get("SKU-S00001").size());
        assertSame(data.getDemandBySku().get("SKU-S00001"), data.getDemandBySku().get("SKU-S00002"));
        assertEquals("WH-SH", data.getSkuWarehouse().get("SKU-S00001"));
        assertEquals("WH-BJ", data.getSkuWarehouse().get("SKU-S00002"));
    }

    @Test
    void clampsToOneHundredThousandSkus() {
        BaselineData data = ScaleCatalog.build(150_000, new BigDecimal("10000001"));
        assertEquals(CapitalTiers.MAX_SKU, data.getDemandBySku().size());
        assertEquals(0, CapitalTiers.MAX_QTY.compareTo(data.getInventory().get(0).getQtyAvailable()));
        assertTrue(data.getDemandBySku().containsKey("SKU-S100000"));
    }
}
