package com.ir.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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

    @Test
    void scaledFreightFollowsRateRatio() {
        assertEquals(0, new BigDecimal("1.40").compareTo(CarrierCodes.rate("SELF01")));
        assertEquals(0, new BigDecimal("70.00").compareTo(
                CarrierCodes.scaledFreight("SF", "SELF01", new BigDecimal("110"))));
        assertEquals(0, new BigDecimal("110").compareTo(
                CarrierCodes.scaledFreight("SF", "SF", new BigDecimal("110"))));
    }
}
