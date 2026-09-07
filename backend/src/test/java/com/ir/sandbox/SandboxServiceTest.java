package com.ir.sandbox;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SandboxServiceTest {
    @Test
    void sharesAccumulateBeforeNormalization() {
        Map<String, Map<String, BigDecimal>> shares = new LinkedHashMap<>();
        SandboxService service = new SandboxService(
                null, null, null, null, null, null, null, null, null);
        service.addShare(shares, "SKU001", "A", BigDecimal.ONE);
        service.addShare(shares, "SKU001", "B", BigDecimal.ONE);
        service.addShare(shares, "SKU001", "A", BigDecimal.ONE);
        service.normalizeShares(shares);
        assertEquals(0, BigDecimal.valueOf(2).divide(BigDecimal.valueOf(3), 6,
                BigDecimal.ROUND_HALF_UP).compareTo(
                shares.get("SKU001").get("A")));
        assertEquals(0, BigDecimal.valueOf(1).divide(BigDecimal.valueOf(3), 6,
                BigDecimal.ROUND_HALF_UP).compareTo(
                shares.get("SKU001").get("B")));
    }
}
