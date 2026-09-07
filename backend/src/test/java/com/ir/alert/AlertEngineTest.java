package com.ir.alert;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AlertEngineTest {
    @Autowired
    private AlertEngine alertEngine;

    @Test
    void stuckOrderRuleFiresAndIsIdempotent() {
        int first = alertEngine.evaluate().size();
        int second = alertEngine.evaluate().size();
        assertTrue(first > 0);
        assertEquals(first, second);
    }
}
