package com.ir.sandbox.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BalancePolicyTest {
    @Test
    void clampKeepsUnitInterval() {
        assertEquals(new BigDecimal("0.5000"), BalancePolicy.clamp(null));
        assertEquals(new BigDecimal("0.0000"), BalancePolicy.clamp(BigDecimal.ZERO));
        assertEquals(new BigDecimal("1.0000"), BalancePolicy.clamp(BigDecimal.TEN));
        assertEquals(new BigDecimal("0.8000"), BalancePolicy.clamp(new BigDecimal("0.8")));
    }

    @Test
    void clampDaysKeepsOneToThirty() {
        assertEquals(3, BalancePolicy.clampDays(null));
        assertEquals(3, BalancePolicy.clampDays(0));
        assertEquals(1, BalancePolicy.clampDays(1));
        assertEquals(30, BalancePolicy.clampDays(90));
        assertEquals(7, BalancePolicy.clampDays(7));
    }
}
