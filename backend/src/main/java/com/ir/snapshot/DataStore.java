package com.ir.snapshot;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DataStore {
    public final List<OrderSnapshot> orders = new CopyOnWriteArrayList<>();
    public final List<WmsOrderSnapshot> wmsOrders = new CopyOnWriteArrayList<>();
    public final List<ShipmentSnapshot> shipments = new CopyOnWriteArrayList<>();
    public final List<InventorySnapshot> inventory = new CopyOnWriteArrayList<>();
    public final List<SalesPoint> sales = new CopyOnWriteArrayList<>();
    public final List<CostRecord> costs = new CopyOnWriteArrayList<>();
    private final Random random = new Random(20250301L);

    @PostConstruct
    public void init() {
        if (!orders.isEmpty()) return;
        String[] warehouses = {"WH-SH", "WH-BJ", "WH-GZ"};
        String[] wms = {"WH01", "WH02", "WH03"};
        String[] skus = {"SKU001", "SKU002", "SKU003", "SKU004", "SKU005"};
        String[] channels = {"TMALL", "JD", "DOUYIN", "OFFLINE", "API"};
        String[] carriers = {"SF", "JDL", "ZTO", "SELF"};
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 300; i++) {
            OrderSnapshot o = new OrderSnapshot();
            o.setOrderNo(String.format("SO%06d", i + 1));
            int wi = i % 3;
            o.setWarehouseCode(warehouses[wi]); o.setChannelCode(channels[i % channels.length]); o.setShopCode("SHOP" + (i % 5 + 1));
            o.setProvince(i % 2 == 0 ? "上海" : i % 3 == 0 ? "北京" : "广东"); o.setCity(o.getProvince() + "市");
            o.setQty(BigDecimal.valueOf(i % 5 + 1)); o.setPayAmount(BigDecimal.valueOf(80 + (i * 17) % 900));
            o.setFreight(BigDecimal.valueOf(6 + i % 4)); o.setOrderTime(now.minusDays(59 - i % 60).minusHours(i % 23));
            o.setPayTime(o.getOrderTime().plusHours(1));
            boolean shipped = i % 7 != 0;
            if (i % 43 == 0) o.setStatus("AUDITED");
            else if (!shipped) o.setStatus(i % 2 == 0 ? "CREATED" : "ALLOCATED");
            else if (i % 31 == 0) o.setStatus("SHIPPED");
            else o.setStatus("COMPLETED");
            if (shipped) {
                o.setShipTime(o.getOrderTime().plusHours(12)); o.setCompleteTime(o.getOrderTime().plusHours(36));
                o.setCarrierCode(carriers[i % carriers.length]); o.setTrackingNo("TRK" + i);
                o.setWmsOrderNo("SO" + String.format("%06d", i + 1)); o.setTmsOrderNo("WB" + String.format("%06d", i + 1));
            }
            orders.add(o);
            WmsOrderSnapshot w = new WmsOrderSnapshot();
            w.setCode(o.getWmsOrderNo() == null ? "SO" + String.format("%06d", i + 1) : o.getWmsOrderNo());
            w.setExternalNo(o.getOrderNo()); w.setWarehouseCode(wms[wi]); w.setTotalQty(o.getQty());
            if (i % 7 == 0) { w.setStatus(i % 2 == 0 ? "PICKING" : "NEW"); w.setPickedQty(i % 2 == 0 ? o.getQty().divide(BigDecimal.valueOf(2)) : BigDecimal.ZERO); }
            else if (i % 31 == 0) { w.setStatus("PACKED"); w.setPickedQty(o.getQty()); }
            else { w.setStatus("SHIPPED"); w.setPickedQty(o.getQty()); w.setShippedQty(o.getQty()); w.setShippedAt(o.getOrderTime().plusHours(12)); }
            w.setCarrier(o.getCarrierCode()); w.setTrackingNo(o.getTrackingNo()); wmsOrders.add(w);
            if (shipped) {
                ShipmentSnapshot s = new ShipmentSnapshot();
                s.setWaybillCode("WB" + String.format("%06d", i + 1)); s.setSourceNo(o.getOrderNo()); s.setCarrierCode(o.getCarrierCode());
                s.setFromSiteCode(wms[wi]); s.setFreightAmount(BigDecimal.valueOf(20 + i % 60));
                if (i % 31 == 0) { s.setStatus("IN_TRANSIT"); s.setPlannedArriveTime(now.minusHours(8)); }
                else { s.setStatus("DELIVERED"); s.setPlannedArriveTime(o.getOrderTime().plusHours(40)); s.setActualArriveTime(o.getOrderTime().plusHours(38)); }
                shipments.add(s);
                CostRecord c = new CostRecord(); c.setBizDate(o.getOrderTime().toLocalDate()); c.setOrderNo(o.getOrderNo());
                c.setWarehouseCode(o.getWarehouseCode()); c.setCarrierCode(o.getCarrierCode()); c.setCostType("FREIGHT"); c.setSourceSystem("TMS"); c.setAmount(s.getFreightAmount()); costs.add(c);
            }
            for (String sku : skus) {
                SalesPoint p = new SalesPoint(); p.setSalesDate(o.getOrderTime().toLocalDate()); p.setSku(sku); p.setWarehouseCode(o.getWarehouseCode()); p.setChannelCode(o.getChannelCode());
                p.setQty(BigDecimal.valueOf(1 + (i + sku.hashCode()) % 4 < 0 ? 1 : 1 + Math.abs(i + sku.hashCode()) % 4));
                p.setAmount(p.getQty().multiply(BigDecimal.valueOf(20 + Math.abs(sku.hashCode()) % 80))); sales.add(p);
            }
        }
        for (int wi = 0; wi < warehouses.length; wi++) for (String sku : skus) {
            InventorySnapshot x = new InventorySnapshot(); x.setSourceSystem("WMS"); x.setWarehouseCode(warehouses[wi]); x.setSku(sku);
            BigDecimal qty = BigDecimal.valueOf((wi + 1) * 55 + Math.abs(sku.hashCode() + wi) % 150);
            if ("SKU005".equals(sku) && wi == 2) qty = BigDecimal.valueOf(3);
            x.setQtyOnHand(qty); x.setQtyReserved(BigDecimal.valueOf(wi + 2)); x.setQtyAvailable(qty.subtract(x.getQtyReserved()));
            x.setSafetyQty(BigDecimal.valueOf(40)); inventory.add(x);
        }
        for (int i = 0; i < 90; i++) for (String sku : skus) for (String wh : warehouses) {
            SalesPoint p = new SalesPoint(); p.setSalesDate(LocalDate.now().minusDays(89 - i)); p.setSku(sku); p.setWarehouseCode(wh); p.setChannelCode("ALL");
            double season = 1 + ((i % 7 == 5 || i % 7 == 6) ? .35 : 0); double trend = 1 + i * .002;
            p.setQty(BigDecimal.valueOf(Math.max(1, Math.round((8 + Math.abs(sku.hashCode() % 9)) * season * trend))));
            p.setAmount(p.getQty().multiply(BigDecimal.valueOf(30 + Math.abs(sku.hashCode() % 50)))); sales.add(p);
        }
        for (OrderSnapshot o : orders) {
            CostRecord h = new CostRecord(); h.setBizDate(o.getOrderTime().toLocalDate()); h.setOrderNo(o.getOrderNo()); h.setWarehouseCode(o.getWarehouseCode()); h.setCostType("HANDLING"); h.setSourceSystem("WMS"); h.setAmount(BigDecimal.valueOf(1.5)); costs.add(h);
            CostRecord p = new CostRecord(); p.setBizDate(o.getOrderTime().toLocalDate()); p.setOrderNo(o.getOrderNo()); p.setWarehouseCode(o.getWarehouseCode()); p.setCostType("PACKAGING"); p.setSourceSystem("WMS"); p.setAmount(BigDecimal.valueOf(.8)); costs.add(p);
        }
        for (int i = 0; i < 6; i++) {
            CostRecord e = new CostRecord(); e.setBizDate(LocalDate.now().minusDays(i * 4)); e.setOrderNo("EX" + i); e.setWarehouseCode(warehouses[i % 3]); e.setCostType("EXCEPTION"); e.setSourceSystem("MOCK"); e.setAmount(BigDecimal.valueOf(25 + i * 5)); costs.add(e);
        }
    }
    public OrderSnapshot order(String no) { for (OrderSnapshot x : orders) if (no.equals(x.getOrderNo())) return x; return null; }
    public WmsOrderSnapshot wms(String no) { for (WmsOrderSnapshot x : wmsOrders) if (no.equals(x.getExternalNo()) || no.equals(x.getCode())) return x; return null; }
    public ShipmentSnapshot shipment(String no) { for (ShipmentSnapshot x : shipments) if (no.equals(x.getSourceNo()) || no.equals(x.getWaybillCode())) return x; return null; }
}
