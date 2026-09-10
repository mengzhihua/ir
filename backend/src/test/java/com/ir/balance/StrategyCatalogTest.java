package com.ir.balance;

import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.ShipmentSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyCatalogTest {
    private final StrategyCatalog catalog = new StrategyCatalog();

    @Test
    void lowStockPrefersTransferWhenDonorHasSurplus() {
        BalanceContext ctx = context();
        ctx.setInventory(Arrays.asList(
                stock("WH-A", "SKU1", 10, 40),
                stock("WH-B", "SKU1", 300, 40)));
        List<Decision> decisions = catalog.stockRebalance(ctx);
        assertEquals(1, decisions.size());
        assertEquals("WMS_REPLENISH", decisions.get(0).getActionType());
        assertEquals("WH-B", decisions.get(0).getParams().get("fromWarehouseCode"));
        assertEquals(new BigDecimal("70"), decisions.get(0).getParams().get("qty"));
    }

    @Test
    void lowStockWithoutDonorCreatesPurchaseSuggestionAndHighAmountNeedsApproval() {
        BalanceContext ctx = context();
        ctx.getConfig().setAutoPurchaseAmountLimit(BigDecimal.valueOf(500));
        ctx.setInventory(Arrays.asList(
                stock("WH-A", "SKU1", 10, 40),
                stock("WH-B", "SKU1", 50, 40)));
        List<Decision> decisions = catalog.stockRebalance(ctx);
        assertEquals(1, decisions.size());
        Decision d = decisions.get(0);
        assertEquals("SRM_PURCHASE_SUGGEST", d.getActionType());
        assertEquals("HIGH", d.getRiskLevel());
        assertTrue(d.isApprovalRequired());
    }

    @Test
    void delayedShipmentTriggersTrackSyncAndExpediteForLateAsn() {
        BalanceContext ctx = context();
        ctx.setInventory(Collections.singletonList(stock("WH-A", "SKU1", 10, 40)));
        ShipmentSnapshot late = new ShipmentSnapshot();
        late.setWaybillCode("WB1");
        late.setStatus("IN_TRANSIT");
        late.setCarrierCode("SF");
        late.setPlannedArriveTime(ctx.getNow().minusHours(6));
        ctx.setShipments(Collections.singletonList(late));
        PurchaseSnapshot asn = new PurchaseSnapshot();
        asn.setDocType("ASN");
        asn.setCode("ASN1");
        asn.setRefCode("PO1");
        asn.setSku("SKU1");
        asn.setStatus("IN_TRANSIT");
        asn.setExpectedDate(LocalDate.now().minusDays(2));
        ctx.setPurchases(Collections.singletonList(asn));

        List<Decision> all = catalog.generate(ctx);
        assertTrue(all.stream().anyMatch(d -> "TMS_SYNC_TRACK".equals(d.getActionType()) && "WB1".equals(d.getTargetKey())));
        assertTrue(all.stream().anyMatch(d -> "SRM_EXPEDITE_PO".equals(d.getActionType()) && "PO1".equals(d.getTargetKey())));
    }

    @Test
    void carrierCostOptimizeIsSkippedWhenCostObjectiveOnTrack() {
        BalanceContext ctx = context();
        ctx.getAttainment().put("COST_PER_ORDER", BigDecimal.ONE);
        ShipmentSnapshot s = new ShipmentSnapshot();
        s.setWaybillCode("WB2");
        s.setStatus("CREATED");
        s.setCarrierCode("SF");
        ctx.setShipments(Collections.singletonList(s));
        assertTrue(catalog.carrierCostOptimize(ctx).isEmpty());
        ctx.getAttainment().put("COST_PER_ORDER", new BigDecimal("0.7"));
        assertFalse(ctx.serviceGuarded());
    }

    private static BalanceContext context() {
        BalanceContext ctx = new BalanceContext();
        ctx.setNow(LocalDateTime.now());
        ctx.setConfig(new BalanceConfig());
        ctx.setMetrics(new LinkedHashMap<>());
        Map<String, BigDecimal> attainment = new LinkedHashMap<>();
        attainment.put("NPS", BigDecimal.ONE);
        attainment.put("OTIF", BigDecimal.ONE);
        ctx.setAttainment(attainment);
        ctx.setWeight(new LinkedHashMap<>());
        ctx.setInventory(new ArrayList<>());
        ctx.setOrders(new ArrayList<>());
        ctx.setShipments(new ArrayList<>());
        ctx.setPurchases(new ArrayList<>());
        ctx.setFreightCosts(new ArrayList<>());
        return ctx;
    }

    private static InventorySnapshot stock(String warehouse, String sku, int available, int safety) {
        InventorySnapshot item = new InventorySnapshot();
        item.setSourceSystem("WMS");
        item.setWarehouseCode(warehouse);
        item.setSku(sku);
        item.setQtyOnHand(BigDecimal.valueOf(available));
        item.setQtyReserved(BigDecimal.ZERO);
        item.setQtyAvailable(BigDecimal.valueOf(available));
        item.setSafetyQty(BigDecimal.valueOf(safety));
        return item;
    }
}
