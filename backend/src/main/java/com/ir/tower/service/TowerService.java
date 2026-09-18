package com.ir.tower.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.cost.service.CostService;
import com.ir.alert.entity.CtAlert;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.sandbox.entity.CtScenario;
import com.ir.sandbox.service.BalancePolicy;
import com.ir.sandbox.service.SandboxService;
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.entity.ExtSnapshot;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.CostRecordMapper;
import com.ir.snapshot.mapper.ExtSnapshotMapper;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
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
    private final ExtSnapshotMapper extSnapshots;
    private final CtAlertMapper alerts;
    private final CtActionMapper actions;
    private final CtSystemMapper systems;
    private final SandboxService sandbox;
    private final BalancePolicy policy;

    public TowerService(
            OrderSnapshotMapper orders,
            WmsOrderSnapshotMapper wmsOrders,
            ShipmentSnapshotMapper shipments,
            InventorySnapshotMapper inventory,
            CostRecordMapper costs,
            ExtSnapshotMapper extSnapshots,
            CtAlertMapper alerts,
            CtActionMapper actions,
            CtSystemMapper systems,
            SandboxService sandbox,
            BalancePolicy policy) {
        this.orders = orders;
        this.wmsOrders = wmsOrders;
        this.shipments = shipments;
        this.inventory = inventory;
        this.costs = costs;
        this.extSnapshots = extSnapshots;
        this.alerts = alerts;
        this.actions = actions;
        this.systems = systems;
        this.sandbox = sandbox;
        this.policy = policy;
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
            if (!Arrays.asList("DELIVERED", "CLOSED", "CANCELLED")
                    .contains(shipment.getStatus())) {
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
        for (CostRecord cost : CostService.preferSettlement(costs.selectList(
                new LambdaQueryWrapper<CostRecord>()
                        .ge(CostRecord::getBizDate, from)))) {
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
        kpi.put("sapLowStock", countExt("SAP", "STOCK", "LOW"));
        kpi.put("srmOpenPr", countExt("SRM", "PR", "DRAFT"));
        kpi.put("dmsShortage", countExt("DMS", "SHORTAGE", "SHORT"));
        kpi.put("crmOpenCases", countExt("CRM", "CASE", "NEW"));
        kpi.put("oaPendingTasks", countExt("OA", "WF_TASK", "PENDING"));
        kpi.put("pendingActions", actions.selectCount(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getStatus, "PENDING")));
        kpi.put("carrierMix", carrierMix(shipmentRows));
        result.put("kpi", kpi);
        result.put("policy", policy.snapshot());

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("oms", countStatuses(orderRows));
        funnel.put("wms", countWmsStatuses(wmsOrders.selectList(null)));
        funnel.put("tms", tms);
        result.put("funnel", funnel);
        result.put("costTrend", costTrend(from));
        result.put("warehouseLoad", warehouseLoad(orderRows, inventoryRows));
        result.put("ecosystem", ecosystem());
        result.put("alertsTop", alerts.selectList(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN")
                .orderByDesc(CtAlert::getCreatedAt)
                .last("LIMIT 10")));
        result.put("systems", systems.selectList(null));
        CtScenario recommendation = sandbox.latestRecommendedAuto();
        if (recommendation != null) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", recommendation.getId());
            rec.put("name", recommendation.getName());
            rec.put("runNo", recommendation.getRunNo());
            rec.put("totalCost", recommendation.getTotalCost());
            rec.put("serviceLevel", recommendation.getServiceLevel());
            rec.put("avgLeadDays", recommendation.getAvgLeadDays());
            rec.put("stockoutUnits", recommendation.getStockoutUnits());
            rec.put("costScore", recommendation.getCostScore());
            rec.put("efficiencyScore", recommendation.getEfficiencyScore());
            rec.put("balanceScore", recommendation.getBalanceScore());
            Map<String, Object> recResult = sandbox.resultOf(recommendation);
            rec.put("capitalVerdict", recResult.get("capitalVerdict"));
            rec.put("capitalUtilization", recResult.get("capitalUtilization"));
            rec.put("cashUsed", recResult.get("cashUsed"));
            rec.put("workingCapital", recResult.get("workingCapital"));
            result.put("recommendation", rec);
        }
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
        for (CostRecord cost : CostService.preferSettlement(costs.selectList(
                new LambdaQueryWrapper<CostRecord>()
                        .ge(CostRecord::getBizDate, from)
                        .orderByAsc(CostRecord::getBizDate)))) {
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

    private Map<String, Object> ecosystem() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String system : Arrays.asList("SAP", "SRM", "BOM", "INV", "CRM", "DMS", "OA")) {
            Map<String, Integer> types = new LinkedHashMap<>();
            for (ExtSnapshot row : extSnapshots.selectList(new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getSourceSystem, system))) {
                types.put(row.getDataType(), types.getOrDefault(row.getDataType(), 0) + 1);
            }
            result.put(system, types);
        }
        return result;
    }

    private Map<String, Integer> carrierMix(List<ShipmentSnapshot> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ShipmentSnapshot shipment : rows) {
            if (Arrays.asList("DELIVERED", "CLOSED", "CANCELLED")
                    .contains(shipment.getStatus())) {
                continue;
            }
            String carrier = com.ir.common.CarrierCodes.toTms(shipment.getCarrierCode());
            result.put(carrier, result.getOrDefault(carrier, 0) + 1);
        }
        return result;
    }

    private long countExt(String system, String dataType, String status) {
        return extSnapshots.selectCount(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, system)
                .eq(ExtSnapshot::getDataType, dataType)
                .eq(ExtSnapshot::getStatus, status));
    }
}
