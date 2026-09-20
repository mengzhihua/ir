package com.ir.sandbox.engine;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalTiersTest {
    @Test
    void presetsCoverFiveScales() {
        assertEquals(5, CapitalTiers.presets().size());
        assertEquals(new BigDecimal("100000"), CapitalTiers.amounts().get(0));
        assertEquals(new BigDecimal("1000000000"), CapitalTiers.amounts().get(4));
        assertEquals("10 万", CapitalTiers.labelOf(new BigDecimal("100000")));
        assertEquals("百万", CapitalTiers.labelOf(new BigDecimal("1000000")));
        assertEquals("千万", CapitalTiers.labelOf(new BigDecimal("10000000")));
        assertEquals("亿", CapitalTiers.labelOf(new BigDecimal("100000000")));
        assertEquals("十亿", CapitalTiers.labelOf(new BigDecimal("1000000000")));
        assertEquals("自定义", CapitalTiers.labelOf(new BigDecimal("500000")));
    }

    @Test
    void clampsHonorSkuQtyAndAmountCaps() {
        assertEquals(100_000, CapitalTiers.clampSku(200_000));
        assertEquals(1, CapitalTiers.clampSku(0));
        assertEquals(0, CapitalTiers.MAX_QTY.compareTo(CapitalTiers.clampQty(new BigDecimal("20000000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(CapitalTiers.clampQty(new BigDecimal("-1"))));
        assertEquals(0, new BigDecimal("100000000").compareTo(CapitalTiers.clampAmount(null)));
        assertEquals(0, CapitalTiers.MAX_AMOUNT.compareTo(CapitalTiers.clampAmount(new BigDecimal("999999999999"))));
    }
}
