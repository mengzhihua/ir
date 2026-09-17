package com.ir.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarrierCodesTest {
    @Test
    void aliasesMapToTmsMasterData() {
        assertEquals("SF", CarrierCodes.toTms("SF"));
        assertEquals("JD", CarrierCodes.toTms("JDL"));
        assertEquals("JD", CarrierCodes.toTms("ZTO"));
        assertEquals("SELF01", CarrierCodes.toTms("SELF"));
        assertEquals("SELF01", CarrierCodes.toTms("SELF01"));
    }

    @Test
    void oneStepCheaperWalksSfToJdToSelf() {
        assertEquals("JD", CarrierCodes.oneStepCheaper("SF"));
        assertEquals("SELF01", CarrierCodes.oneStepCheaper("JD"));
        assertEquals(null, CarrierCodes.oneStepCheaper("SELF01"));
    }
}
