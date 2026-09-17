package com.ir.sandbox;

import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.ShipmentSnapshot;
import org.junit.jupiter.api.Test;

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
