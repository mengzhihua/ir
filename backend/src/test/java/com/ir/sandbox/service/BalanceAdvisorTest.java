package com.ir.sandbox.service;

import org.junit.jupiter.api.Test;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceAdvisorTest {
    @Test
    void costFirstPicksSelfFleetAndEfficiencyFirstPicksJd() {
        assertEquals("SELF01", new BalanceAdvisor(BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2))
                .pickCarrier("SF"));
        assertEquals("JD", new BalanceAdvisor(BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8))
                .pickCarrier("SELF01"));
    }

    @Test
    void delayOnExpensiveCarrierSwitchesWhenCostLeads() {
        ShipmentSnapshot shipment = waybill("WB-COST", "SF", "IN_TRANSIT", "WH01");
        BalanceAdvisor.Advice cost = new BalanceAdvisor(
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2)).adviseDelay(shipment);
        assertEquals(BalanceAdvisor.SWITCH, cost.getType());
        assertEquals("WB-COST", cost.getTargetKey());
        assertEquals("SELF01", cost.getCarrierCode());

        BalanceAdvisor.Advice fast = new BalanceAdvisor(
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8)).adviseDelay(shipment);
        assertEquals(BalanceAdvisor.SYNC, fast.getType());
        assertEquals("SF", fast.getCarrierCode());
    }

    @Test
    void delayOnSlowCarrierSwitchesFasterWhenEfficiencyLeads() {
        ShipmentSnapshot shipment = waybill("WB-SLOW", "SELF01", "IN_TRANSIT", "WH01");
        BalanceAdvisor.Advice advice = new BalanceAdvisor(
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8)).adviseDelay(shipment);
        assertEquals(BalanceAdvisor.SWITCH, advice.getType());
        assertEquals("JD", advice.getCarrierCode());
    }

    @Test
    void overrunMapsWarehouseToOpenWaybillAndStepsCarrier() {
        ShipmentSnapshot open = waybill("WB-IR-DELAY", "SF", "IN_TRANSIT", "WH01");
        ShipmentSnapshot done = waybill("WB-DONE", "SF", "DELIVERED", "WH01");
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("IR-SO-STUCK");
        order.setWarehouseCode("WH-SH");
        open.setSourceNo("IR-SO-STUCK");

        BalanceAdvisor balanced = new BalanceAdvisor(
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5));
        java.util.List<BalanceAdvisor.Advice> advice = balanced.adviseOverrun(
                "WH-SH", Arrays.asList(open, done), Collections.singletonList(order));
        assertEquals(1, advice.size());
        assertEquals("WB-IR-DELAY", advice.get(0).getTargetKey());
        assertEquals("JD", advice.get(0).getCarrierCode());

        BalanceAdvisor cheap = new BalanceAdvisor(
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2));
        assertEquals("SELF01", cheap.adviseOverrun(
                "WH-SH", Collections.singletonList(open),
                Collections.singletonList(order)).get(0).getCarrierCode());
    }

    @Test
    void efficiencyFirstOverrunOnlyDropsOneStepFromSf() {
        ShipmentSnapshot open = waybill("WB-FAST", "SF", "IN_TRANSIT", "WH01");
        BalanceAdvisor.Advice advice = new BalanceAdvisor(
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8))
                .adviseOverrun("WH-SH", Collections.singletonList(open),
                        Collections.<OrderSnapshot>emptyList()).get(0);
        assertEquals("JD", advice.getCarrierCode());
        assertTrue(advice.params().containsKey("carrierCode"));
    }

    @Test
    void auditedStuckHoldsWhenCostLeadsAndAutoProcessesWhenEfficiencyLeads() {
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("SO-STUCK");
        order.setStatus("AUDITED");
        order.setWarehouseCode("WH-SH");
        assertEquals("OMS_HOLD", new BalanceAdvisor(BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2))
                .adviseStuckOrder(order, "OMS_STUCK").getType());
        assertEquals("OMS_AUTO_PROCESS", new BalanceAdvisor(BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8))
                .adviseStuckOrder(order, "OMS_STUCK").getType());
        assertEquals("OMS_PRIORITIZE", new BalanceAdvisor(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5))
                .adviseStuckOrder(order, "OMS_STUCK").getType());
    }

    @Test
    void stockoutCostFirstSkipsWarehouseRush() {
        java.util.List<BalanceAdvisor.Advice> cheap = new BalanceAdvisor(
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2))
                .adviseStockout("SKU001", "WH-SH", BigDecimal.TEN);
        assertEquals(1, cheap.size());
        assertEquals("SRM_PURCHASE_SUGGEST", cheap.get(0).getType());
        assertEquals(new BigDecimal("15"), cheap.get(0).params().get("qty"));

        java.util.List<BalanceAdvisor.Advice> fast = new BalanceAdvisor(
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8))
                .adviseStockout("SKU001", "WH-SH", BigDecimal.TEN);
        assertEquals(2, fast.size());
        assertEquals("WMS_REPLENISH", fast.get(1).getType());
        assertEquals("WH-SH", fast.get(1).getTargetKey());
    }

    @Test
    void wmsStuckHoldsWhenCostLeadsAndAllocatesOtherwise() {
        WmsOrderSnapshot outbound = new WmsOrderSnapshot();
        outbound.setCode("SO-IR-STUCK");
        outbound.setExternalNo("IR-SO-STUCK");
        outbound.setWarehouseCode("WH-SH");
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("IR-SO-STUCK");
        assertEquals("OMS_HOLD", new BalanceAdvisor(BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2))
                .adviseWmsStuck(outbound, order).getType());
        assertEquals("WMS_ALLOCATE", new BalanceAdvisor(BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8))
                .adviseWmsStuck(outbound, order).getType());
        assertEquals("WMS_ALLOCATE", new BalanceAdvisor(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5))
                .adviseWmsStuck(outbound, order).getType());
    }

    @Test
    void opposingPendingDetectsRushVsHold() {
        org.junit.jupiter.api.Assertions.assertTrue(
                BalanceAdvisor.opposes("COST", "OMS_AUTO_PROCESS", null));
        org.junit.jupiter.api.Assertions.assertTrue(
                BalanceAdvisor.opposes("COST", "TMS_SWITCH_CARRIER", "SF"));
        org.junit.jupiter.api.Assertions.assertFalse(
                BalanceAdvisor.opposes("COST", "TMS_SWITCH_CARRIER", "SELF01"));
        org.junit.jupiter.api.Assertions.assertTrue(
                BalanceAdvisor.opposes("EFFICIENCY", "OMS_HOLD", null));
        org.junit.jupiter.api.Assertions.assertTrue(
                BalanceAdvisor.opposes("EFFICIENCY", "TMS_SWITCH_CARRIER", "SELF01"));
    }

    @Test
    void freightSavingUsesRateDeltaAndShareSplitsScenario() {
        assertEquals(0, new BigDecimal("40.00").compareTo(
                BalanceAdvisor.freightSaving("SF", "SELF01", new BigDecimal("110"))));
        assertEquals(0, new BigDecimal("70.00").compareTo(
                com.ir.common.CarrierCodes.scaledFreight(
                        "SF", "SELF01", new BigDecimal("110"))));
        assertEquals(0, new BigDecimal("18.18").compareTo(
                BalanceAdvisor.freightSaving("SF", "JD", new BigDecimal("100"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                BalanceAdvisor.freightSaving("SELF01", "SF", new BigDecimal("100"))));
        assertEquals(0, new BigDecimal("12.50").compareTo(
                BalanceAdvisor.shareSaving(new BigDecimal("50"), 4)));
        assertEquals(0, BigDecimal.ZERO.compareTo(BalanceAdvisor.shareSaving(BigDecimal.TEN, 0)));

        ShipmentSnapshot shipment = waybill("WB-SAVE", "SF", "IN_TRANSIT", "WH01");
        shipment.setFreightAmount(new BigDecimal("110"));
        BalanceAdvisor.Advice cost = new BalanceAdvisor(
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.2)).adviseDelay(shipment);
        assertEquals(BalanceAdvisor.SWITCH, cost.getType());
        assertEquals(0, new BigDecimal("40.00").compareTo(cost.getExpectedSaving()));
        BalanceAdvisor.Advice sync = new BalanceAdvisor(
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8)).adviseDelay(shipment);
        assertEquals(BalanceAdvisor.SYNC, sync.getType());
        org.junit.jupiter.api.Assertions.assertNull(sync.getExpectedSaving());
    }

    private ShipmentSnapshot waybill(
            String code, String carrier, String status, String site) {
        ShipmentSnapshot shipment = new ShipmentSnapshot();
        shipment.setWaybillCode(code);
        shipment.setCarrierCode(carrier);
        shipment.setStatus(status);
        shipment.setFromSiteCode(site);
        return shipment;
    }
}
