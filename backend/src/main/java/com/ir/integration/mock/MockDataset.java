package com.ir.integration.mock;

import com.ir.snapshot.CostRecord;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.SalesPoint;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.WmsOrderSnapshot;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class MockDataset {
    private static final List<String> OMS_WAREHOUSES =
            Arrays.asList("WH-SH", "WH-BJ", "WH-GZ");
    private static final List<String> WMS_WAREHOUSES =
            Arrays.asList("WH01", "WH02", "WH03");
    private static final List<String> SKUS =
            Arrays.asList("SKU001", "SKU002", "SKU003", "SKU004", "SKU005");
    private static final List<String> CHANNELS =
            Arrays.asList("TMALL", "JD", "DOUYIN", "OFFLINE", "API");
    private static final List<String> CARRIERS =
            Arrays.asList("SF", "JDL", "ZTO", "SELF");

    private final List<OrderSnapshot> orders = new ArrayList<>();
    private final List<WmsOrderSnapshot> outbound = new ArrayList<>();
    private final List<ShipmentSnapshot> shipments = new ArrayList<>();
    private final List<InventorySnapshot> inventory = new ArrayList<>();
    private final List<SalesPoint> sales = new ArrayList<>();
    private final List<CostRecord> costs = new ArrayList<>();

    @PostConstruct
    public void generate() {
        Random random = new Random(20250301L);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        for (int i = 0; i < 300; i++) {
            OrderSnapshot order = createOrder(i, now);
            orders.add(order);
            outbound.add(createOutbound(i, order));
            if (order.getShipTime() != null) {
                shipments.add(createShipment(i, order, now));
            }
            createOrderCosts(order);
        }

        createInventory(random);
        createSales(random);
        createExceptionCosts();
    }

    private OrderSnapshot createOrder(int index, LocalDateTime now) {
        int warehouseIndex = index % OMS_WAREHOUSES.size();
        LocalDateTime orderTime = now.minusDays(59L - index % 60)
                .minusHours(index % 23);
        boolean shipped = index % 7 != 0;

        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo(String.format("SO%06d", index + 1));
        order.setSku(SKUS.get(index % SKUS.size()));
        order.setChannelCode(CHANNELS.get(index % CHANNELS.size()));
        order.setShopCode("SHOP" + (index % 5 + 1));
        order.setWarehouseCode(OMS_WAREHOUSES.get(warehouseIndex));
        order.setProvince(index % 2 == 0 ? "上海" : index % 3 == 0 ? "北京" : "广东");
        order.setCity(order.getProvince() + "市");
        order.setQty(BigDecimal.valueOf(index % 5 + 1));
        order.setPayAmount(BigDecimal.valueOf(80 + (index * 17) % 900));
        order.setFreight(BigDecimal.valueOf(6 + index % 4));
        order.setOrderTime(orderTime);
        order.setPayTime(orderTime.plusHours(1));

        if (index % 43 == 0) {
            order.setStatus("AUDITED");
        } else if (!shipped && index % 11 == 0) {
            order.setStatus("PAID");
        } else if (!shipped) {
            order.setStatus(index % 2 == 0 ? "CREATED" : "ALLOCATED");
        } else if (index % 31 == 0) {
            order.setStatus("SHIPPED");
        } else {
            order.setStatus("COMPLETED");
        }

        if (shipped) {
            order.setShipTime(orderTime.plusHours(12));
            order.setCompleteTime(orderTime.plusHours(36));
            order.setCarrierCode(CARRIERS.get(index % CARRIERS.size()));
            order.setTrackingNo("TRK" + index);
            order.setWmsOrderNo(order.getOrderNo());
            order.setTmsOrderNo("WB" + String.format("%06d", index + 1));
        }
        return order;
    }

    private WmsOrderSnapshot createOutbound(int index, OrderSnapshot order) {
        WmsOrderSnapshot outboundOrder = new WmsOrderSnapshot();
        outboundOrder.setCode(order.getOrderNo());
        outboundOrder.setExternalNo(order.getOrderNo());
        outboundOrder.setSku(order.getSku());
        outboundOrder.setWarehouseCode(WMS_WAREHOUSES.get(index % WMS_WAREHOUSES.size()));
        outboundOrder.setTotalQty(order.getQty());
        outboundOrder.setCarrier(order.getCarrierCode());
        outboundOrder.setTrackingNo(order.getTrackingNo());

        if (index % 7 == 0) {
            outboundOrder.setStatus(index % 2 == 0 ? "PICKING" : "NEW");
            outboundOrder.setPickedQty(index % 2 == 0
                    ? order.getQty().divide(BigDecimal.valueOf(2))
                    : BigDecimal.ZERO);
            outboundOrder.setShippedQty(BigDecimal.ZERO);
        } else if (index % 31 == 0) {
            outboundOrder.setStatus("PACKED");
            outboundOrder.setPickedQty(order.getQty());
            outboundOrder.setShippedQty(BigDecimal.ZERO);
        } else {
            outboundOrder.setStatus("SHIPPED");
            outboundOrder.setPickedQty(order.getQty());
            outboundOrder.setShippedQty(order.getQty());
            outboundOrder.setShippedAt(order.getShipTime());
        }
        return outboundOrder;
    }

    private ShipmentSnapshot createShipment(int index, OrderSnapshot order, LocalDateTime now) {
        ShipmentSnapshot shipment = new ShipmentSnapshot();
        shipment.setWaybillCode("WB" + String.format("%06d", index + 1));
        shipment.setSourceNo(order.getOrderNo());
        shipment.setCarrierCode(order.getCarrierCode());
        shipment.setFromSiteCode(WMS_WAREHOUSES.get(index % WMS_WAREHOUSES.size()));
        shipment.setFreightAmount(BigDecimal.valueOf(20 + index % 60));
        shipment.setExceptionFlag(index % 37 == 0);

        if (index % 31 == 0 || shipment.getExceptionFlag()) {
            shipment.setStatus("IN_TRANSIT");
            shipment.setPlannedArriveTime(now.minusHours(8));
        } else {
            shipment.setStatus("DELIVERED");
            shipment.setPlannedArriveTime(order.getOrderTime().plusHours(40));
            shipment.setActualArriveTime(order.getOrderTime().plusHours(38));
        }
        return shipment;
    }

    private void createOrderCosts(OrderSnapshot order) {
        CostRecord handling = new CostRecord();
        handling.setBizDate(order.getOrderTime().toLocalDate());
        handling.setOrderNo(order.getOrderNo());
        handling.setWarehouseCode(order.getWarehouseCode());
        handling.setCostType("HANDLING");
        handling.setSourceSystem("WMS");
        handling.setAmount(BigDecimal.valueOf(1.50));
        costs.add(handling);

        CostRecord packaging = new CostRecord();
        packaging.setBizDate(order.getOrderTime().toLocalDate());
        packaging.setOrderNo(order.getOrderNo());
        packaging.setWarehouseCode(order.getWarehouseCode());
        packaging.setCostType("PACKAGING");
        packaging.setSourceSystem("WMS");
        packaging.setAmount(BigDecimal.valueOf(0.80));
        costs.add(packaging);
    }

    private void createInventory(Random random) {
        for (int warehouseIndex = 0; warehouseIndex < OMS_WAREHOUSES.size(); warehouseIndex++) {
            for (String sku : SKUS) {
                InventorySnapshot item = new InventorySnapshot();
                item.setSourceSystem("WMS");
                item.setWarehouseCode(OMS_WAREHOUSES.get(warehouseIndex));
                item.setSku(sku);
                BigDecimal quantity = BigDecimal.valueOf(
                        (warehouseIndex + 1) * 55L + random.nextInt(150));
                if ("SKU005".equals(sku) && warehouseIndex == 2) {
                    quantity = BigDecimal.valueOf(3);
                }
                item.setQtyOnHand(quantity);
                item.setQtyReserved(BigDecimal.valueOf(warehouseIndex + 2L));
                item.setQtyAvailable(quantity.subtract(item.getQtyReserved()));
                item.setSafetyQty(BigDecimal.valueOf(40));
                inventory.add(item);
            }
        }
    }

    private void createSales(Random random) {
        for (int day = 0; day < 90; day++) {
            LocalDate date = LocalDate.now().minusDays(89L - day);
            for (String sku : SKUS) {
                for (String warehouse : OMS_WAREHOUSES) {
                    double seasonal = day % 7 >= 5 ? 1.35 : 1.0;
                    double trend = 1.0 + day * 0.002;
                    double noise = 0.90 + random.nextDouble() * 0.20;
                    BigDecimal quantity = BigDecimal.valueOf(Math.max(1,
                            Math.round((8 + Math.abs(sku.hashCode() % 9))
                                    * seasonal * trend * noise)));
                    SalesPoint point = new SalesPoint();
                    point.setSalesDate(date);
                    point.setSku(sku);
                    point.setWarehouseCode(warehouse);
                    point.setChannelCode("ALL");
                    point.setQty(quantity);
                    point.setAmount(quantity.multiply(
                            BigDecimal.valueOf(30 + Math.abs(sku.hashCode() % 50))));
                    sales.add(point);
                }
            }
        }
    }

    private void createExceptionCosts() {
        for (int index = 0; index < 6; index++) {
            CostRecord exception = new CostRecord();
            exception.setBizDate(LocalDate.now().minusDays(index * 4L));
            exception.setOrderNo("EX" + index);
            exception.setWarehouseCode(OMS_WAREHOUSES.get(index % OMS_WAREHOUSES.size()));
            exception.setCostType("EXCEPTION");
            exception.setSourceSystem("MOCK");
            exception.setAmount(BigDecimal.valueOf(25 + index * 5L));
            exception.setRemark("演示异常成本");
            costs.add(exception);
        }
    }

    public List<OrderSnapshot> orders() {
        return orders;
    }

    public List<WmsOrderSnapshot> outbound() {
        return outbound;
    }

    public List<ShipmentSnapshot> shipments() {
        return shipments;
    }

    public List<InventorySnapshot> inventory() {
        return inventory;
    }

    public List<SalesPoint> sales() {
        return sales;
    }

    public List<CostRecord> costs() {
        return costs;
    }

    public List<String> skus() {
        return Collections.unmodifiableList(SKUS);
    }
}
