package com.ir.objective;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectiveServiceTest {
    @Test
    void attainmentHandlesMinAndMaxDirections() {
        CtObjective min = new CtObjective();
        min.setDirection("MIN");
        min.setTargetValue(BigDecimal.valueOf(18));
        assertEquals(new BigDecimal("0.9000"), ObjectiveService.attainment(min, BigDecimal.valueOf(20)));
        assertEquals(BigDecimal.ONE, ObjectiveService.attainment(min, BigDecimal.valueOf(10)));

        CtObjective max = new CtObjective();
        max.setDirection("MAX");
        max.setTargetValue(BigDecimal.valueOf(50));
        assertEquals(new BigDecimal("0.5000"), ObjectiveService.attainment(max, BigDecimal.valueOf(25)));
        assertEquals(BigDecimal.ZERO, ObjectiveService.attainment(max, BigDecimal.valueOf(-10)));
        assertEquals("OFF_TRACK", ObjectiveService.status(new BigDecimal("0.5")));
        assertEquals("AT_RISK", ObjectiveService.status(new BigDecimal("0.85")));
        assertEquals("ON_TRACK", ObjectiveService.status(new BigDecimal("0.96")));
    }
}
