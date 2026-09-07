package com.ir.tower;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.alert.CtAlert;
import com.ir.alert.CtAlertMapper;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TowerService {
    private final OrderSnapshotMapper orders;
    private final WmsOrderSnapshotMapper wmsOrders;
    private final ShipmentSnapshotMapper shipments;
    private final InventorySnapshotMapper inventory;
    private final CostRecordMapper costs;
    private final CtAlertMapper alerts;
    private final CtSystemMapper systems;

    public TowerService(
            OrderSnapshotMapper orders,
            WmsOrderSnapshotMapper wmsOrders,
            ShipmentSnapshotMapper shipments,
            InventorySnapshotMapper inventory,
            CostRecordMapper costs,
            CtAlertMapper alerts,
            CtSystemMapper systems) {
        this.orders = orders;
        this.wmsOrders = wmsOrders;
        this.shipments = shipments;
        this.inventory = inventory;
        this.costs = costs;
        this.alerts = alerts;
        this.systems = systems;
    }

    public Map<String, Object> overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate from = today.minusDays(29);
        List<OrderSnapshot> orderRows = orders.selectList(null);
        List<ShipmentSnapshot> shipmentRows = shipments.selectList(null);
        List<InventorySnapshot> inventoryRows = inventory.selectList(null);
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal leadTotal = BigDecimal.ZERO;
        int leadCount = 0;
        int todayOrders = 0;
        int pendingOrders = 0;
        for (OrderSnapshot order : orderRows) {
            if (today.equals(order.getOrderTime().toLocalDate())) {
                todayOrders++;
            }
            if (!Arrays.asList("COMPLETED", "CANCELLED")
                    .contains(order.getStatus())) {
                pendingOrders++;
            }
            if (order.getCompleteTime() != null) {
                leadTotal = leadTotal.add(BigDecimal.valueOf(Duration.between(
                        order.getOrderTime(), order.getCompleteTime()).toHours()));
                leadCount++;
            }
        }
        int inTransit = 0;
        int delayed = 0;
        int deliveredOnTime = 0;
        int deliveredTotal = 0;
        Map<String, Integer> tms = new LinkedHashMap<>();
        for (ShipmentSnapshot shipment : shipmentRows) {
            tms.put(shipment.getStatus(),
                    tms.getOrDefault(shipment.getStatus(), 0) + 1);
            if ("IN_TRANSIT".equals(shipment.getStatus())) {
                inTransit++;
            }
            if (shipment.getPlannedArriveTime() != null
                    && shipment.getPlannedArriveTime().isBefore(now)
                    && !Arrays.asList("DELIVERED", "CLOSED")
                    .contains(shipment.getStatus())) {
                delayed++;
            }
            if (Arrays.asList("DELIVERED", "CLOSED")
                    .contains(shipment.getStatus())) {
                deliveredTotal++;
                if (shipment.getActualArriveTime() != null
                        && !shipment.getActualArriveTime()
                        .isAfter(shipment.getPlannedArriveTime())) {
                    deliveredOnTime++;
                }
            }
        }
        int lowStock = 0;
        for (InventorySnapshot item : inventoryRows) {
            if (item.getQtyAvailable().compareTo(item.getSafetyQty()) < 0) {
                lowStock++;
            }
        }
        for (CostRecord cost : costs.selectList(new LambdaQueryWrapper<CostRecord>()
                .ge(CostRecord::getBizDate, from))) {
            totalCost = totalCost.add(cost.getAmount());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("todayOrders", todayOrders);
        kpi.put("pendingOrders", pendingOrders);
        kpi.put("inTransit", inTransit);
        kpi.put("delayedShipments", delayed);
        kpi.put("lowStockSkus", lowStock);
        kpi.put("openAlerts", alerts.selectCount(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN")));
        kpi.put("totalCost30d", totalCost);
        kpi.put("costPerOrder30d", orderRows.isEmpty() ? BigDecimal.ZERO
                : totalCost.divide(BigDecimal.valueOf(orderRows.size()), 2,
                RoundingMode.HALF_UP));
        kpi.put("otif30d", deliveredTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(deliveredOnTime)
                .divide(BigDecimal.valueOf(deliveredTotal), 4,
                        RoundingMode.HALF_UP));
        kpi.put("avgLeadTimeHours", leadCount == 0 ? BigDecimal.ZERO
                : leadTotal.divide(BigDecimal.valueOf(leadCount), 2,
                        RoundingMode.HALF_UP));
        result.put("kpi", kpi);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("oms", countStatuses(orderRows));
        funnel.put("wms", countWmsStatuses(wmsOrders.selectList(null)));
        funnel.put("tms", tms);
        result.put("funnel", funnel);
        result.put("costTrend", costTrend(from));
        result.put("warehouseLoad", warehouseLoad(orderRows, inventoryRows));
        result.put("alertsTop", alerts.selectList(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN")
                .orderByDesc(CtAlert::getCreatedAt)
                .last("LIMIT 10")));
        result.put("systems", systems.selectList(null));
        return result;
    }

    private Map<String, Integer> countStatuses(List<OrderSnapshot> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (OrderSnapshot row : rows) {
            result.put(row.getStatus(), result.getOrDefault(row.getStatus(), 0) + 1);
        }
        return result;
    }

    private Map<String, Integer> countWmsStatuses(List<WmsOrderSnapshot> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (WmsOrderSnapshot row : rows) {
            result.put(row.getStatus(), result.getOrDefault(row.getStatus(), 0) + 1);
        }
        return result;
    }

    private List<Map<String, Object>> costTrend(LocalDate from) {
        Map<LocalDate, Map<String, BigDecimal>> grouped = new LinkedHashMap<>();
        for (CostRecord cost : costs.selectList(new LambdaQueryWrapper<CostRecord>()
                .ge(CostRecord::getBizDate, from)
                .orderByAsc(CostRecord::getBizDate))) {
            grouped.computeIfAbsent(cost.getBizDate(),
                    key -> new LinkedHashMap<>())
                    .put(cost.getCostType(),
                            grouped.get(cost.getBizDate())
                                    .getOrDefault(cost.getCostType(),
                                            BigDecimal.ZERO)
                                    .add(cost.getAmount()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry
                : grouped.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("byType", entry.getValue());
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> warehouseLoad(
            List<OrderSnapshot> orderRows,
            List<InventorySnapshot> inventoryRows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String warehouse : Arrays.asList("WH-SH", "WH-BJ", "WH-GZ")) {
            int pending = 0;
            int low = 0;
            BigDecimal quantity = BigDecimal.ZERO;
            for (OrderSnapshot order : orderRows) {
                if (warehouse.equals(order.getWarehouseCode())
                        && !Arrays.asList("COMPLETED", "CANCELLED")
                        .contains(order.getStatus())) {
                    pending++;
                }
            }
            for (InventorySnapshot item : inventoryRows) {
                if (warehouse.equals(item.getWarehouseCode())) {
                    quantity = quantity.add(item.getQtyAvailable());
                    if (item.getQtyAvailable().compareTo(item.getSafetyQty()) < 0) {
                        low++;
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseCode", warehouse);
            row.put("pendingOrders", pending);
            row.put("inventoryQty", quantity);
            row.put("lowStockSkus", low);
            result.add(row);
        }
        return result;
    }
}
