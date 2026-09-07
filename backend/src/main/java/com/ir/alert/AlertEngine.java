package com.ir.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.forecast.ForecastService;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import com.ir.common.CodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlertEngine {
    private final CtRuleMapper ruleMapper;
    private final CtAlertMapper alertMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;
    private final CostRecordMapper costMapper;
    private final ForecastService forecastService;

    public AlertEngine(
            CtRuleMapper ruleMapper,
            CtAlertMapper alertMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper,
            CostRecordMapper costMapper,
            ForecastService forecastService) {
        this.ruleMapper = ruleMapper;
        this.alertMapper = alertMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
        this.costMapper = costMapper;
        this.forecastService = forecastService;
    }

    @Transactional
    public synchronized List<CtAlert> evaluate() {
        List<CtRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<CtRule>().eq(CtRule::getEnabled, true));
        LocalDateTime now = LocalDateTime.now();
        for (CtRule rule : rules) {
            Map<String, Object> params = params(rule.getParams());
            if ("ORDER_STUCK".equals(rule.getType())) {
                evaluateOrders(rule, params, now);
            } else if ("WMS_STUCK".equals(rule.getType())) {
                evaluateWms(rule, params, now);
            } else if ("TMS_DELAY".equals(rule.getType())) {
                evaluateShipments(rule, now);
            } else if ("LOW_STOCK".equals(rule.getType())) {
                evaluateInventory(rule);
            } else if ("COST_OVERRUN".equals(rule.getType())) {
                evaluateCost(rule, params);
            } else if ("FORECAST_STOCKOUT".equals(rule.getType())) {
                evaluateForecast(rule, params);
            }
        }
        return page(null);
    }

    public List<CtAlert> page(String status) {
        LambdaQueryWrapper<CtAlert> query = new LambdaQueryWrapper<>();
        if (status != null) {
            query.eq(CtAlert::getStatus, status);
        }
        query.orderByDesc(CtAlert::getCreatedAt);
        return alertMapper.selectList(query);
    }

    public CtAlert update(Long id, String status) {
        CtAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            return null;
        }
        alert.setStatus(status);
        if ("RESOLVED".equals(status)) {
            alert.setResolvedAt(LocalDateTime.now());
        }
        alertMapper.updateById(alert);
        return alert;
    }

    public CtAction executeSuggested(Long id) {
        CtAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            return null;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", alert.getSuggestedAction());
        request.put("targetKey", alert.getTargetKey());
        request.put("alertId", id);
        CtAction action = actions.createAndExecute(request);
        alert.setActionId(action.getId());
        alertMapper.updateById(alert);
        return action;
    }

    private void evaluateOrders(CtRule rule, Map<String, Object> params, LocalDateTime now) {
        String expectedStatus = String.valueOf(params.get("status"));
        long threshold = number(params.get("hours"), 4L);
        for (OrderSnapshot order : orderMapper.selectList(null)) {
            if (expectedStatus.equals(order.getStatus())
                    && order.getOrderTime() != null
                    && Duration.between(order.getOrderTime(), now).toHours() > threshold) {
                add(rule, "ORDER", order.getOrderNo(), order.getWarehouseCode(),
                        "订单卡单", expectedStatus + " 超过 " + threshold + " 小时");
            }
        }
    }

    private void evaluateWms(CtRule rule, Map<String, Object> params, LocalDateTime now) {
        long threshold = number(params.get("hours"), 6L);
        for (WmsOrderSnapshot outbound : wmsMapper.selectList(null)) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, outbound.getExternalNo()));
            if (order != null && "PICKING".equals(outbound.getStatus())
                    && Duration.between(order.getOrderTime(), now).toHours() > threshold) {
                add(rule, "ORDER", order.getOrderNo(), order.getWarehouseCode(),
                        "WMS 拣货卡单", "PICKING 超过 " + threshold + " 小时");
            }
        }
    }

    private void evaluateShipments(CtRule rule, LocalDateTime now) {
        boolean exceptionOnly = String.valueOf(rule.getParams())
                .contains("\"exception\":true");
        for (ShipmentSnapshot shipment : shipmentMapper.selectList(null)) {
            if ((exceptionOnly && !Boolean.TRUE.equals(shipment.getExceptionFlag()))
                    || (!exceptionOnly && shipment.getPlannedArriveTime() == null)) {
                continue;
            }
            if ((exceptionOnly && Boolean.TRUE.equals(shipment.getExceptionFlag()))
                    || (shipment.getPlannedArriveTime() != null
                    && shipment.getPlannedArriveTime().isBefore(now)
                    && !"DELIVERED".equals(shipment.getStatus())
                    && !"CLOSED".equals(shipment.getStatus()))) {
                add(rule, "WAYBILL", shipment.getWaybillCode(), null,
                        "运输到达延迟", "计划到达时间已过");
            }
        }
    }

    private void evaluateCost(CtRule rule, Map<String, Object> params) {
        LocalDate from = LocalDate.now().minusDays(number(
                params.get("days"), 7L) - 1L);
        java.util.Map<String, java.math.BigDecimal> amountByWarehouse =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> ordersByWarehouse =
                new java.util.LinkedHashMap<>();
        for (CostRecord row : costMapper.selectList(
                new LambdaQueryWrapper<CostRecord>()
                        .ge(CostRecord::getBizDate, from))) {
            String warehouse = row.getWarehouseCode() == null
                    ? "UNKNOWN" : row.getWarehouseCode();
            amountByWarehouse.put(warehouse,
                    amountByWarehouse.getOrDefault(warehouse,
                            java.math.BigDecimal.ZERO).add(row.getAmount()));
            ordersByWarehouse.computeIfAbsent(warehouse,
                    key -> new java.util.LinkedHashSet<>()).add(
                    row.getOrderNo() == null ? row.getId().toString()
                            : row.getOrderNo());
        }
        java.math.BigDecimal threshold = new java.math.BigDecimal(
                String.valueOf(params.getOrDefault(
                        "costPerOrderThreshold", params.getOrDefault(
                                "threshold", "0"))));
        for (String warehouse : amountByWarehouse.keySet()) {
            int count = ordersByWarehouse.get(warehouse).size();
            java.math.BigDecimal perOrder = amountByWarehouse.get(warehouse)
                    .divide(java.math.BigDecimal.valueOf(Math.max(1, count)),
                            4, java.math.RoundingMode.HALF_UP);
            if (perOrder.compareTo(threshold) > 0) {
                add(rule, "WAREHOUSE", warehouse, warehouse,
                        "仓库成本超标", "近 " + params.getOrDefault(
                                "days", 7) + " 天单均成本 " + perOrder);
            }
        }
    }

    private void evaluateForecast(CtRule rule, Map<String, Object> params) {
        int horizon = (int) number(params.get("horizon"),
                number(params.get("days"), 14L));
        int serviceDays = (int) number(params.get("serviceDays"), 3L);
        LocalDate limit = LocalDate.now().plusDays(horizon);
        for (Map<String, Object> row : forecastService.replenish(
                null, horizon, serviceDays)) {
            Object stockoutValue = row.get("stockoutDate");
            if (stockoutValue == null) {
                continue;
            }
            LocalDate stockout = stockoutValue instanceof LocalDate
                    ? (LocalDate) stockoutValue
                    : LocalDate.parse(String.valueOf(stockoutValue));
            if (!stockout.isAfter(limit)) {
                String sku = String.valueOf(row.get("sku"));
                String warehouse = String.valueOf(row.get("warehouseCode"));
                add(rule, "SKU_WAREHOUSE", sku + "/" + warehouse,
                        warehouse, "预测即将缺货",
                        "预计 " + stockout + " 缺货");
            }
        }
    }

    private void evaluateInventory(CtRule rule) {
        for (InventorySnapshot inventory : inventoryMapper.selectList(null)) {
            if (inventory.getQtyAvailable().compareTo(inventory.getSafetyQty()) < 0) {
                add(rule, "SKU", inventory.getSku(), inventory.getWarehouseCode(),
                        "低库存", "可用库存低于安全库存");
            }
        }
    }

    private void add(
            CtRule rule,
            String targetType,
            String targetKey,
            String warehouse,
            String title,
            String detail) {
        CtAlert existing = alertMapper.selectOne(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getRuleCode, rule.getCode())
                .eq(CtAlert::getTargetKey, targetKey)
                .eq(CtAlert::getStatus, "OPEN"));
        if (existing != null) {
            return;
        }
        CtAlert alert = new CtAlert();
        alert.setAlertNo(codes.next("ALT"));
        alert.setRuleCode(rule.getCode());
        alert.setType(rule.getType());
        alert.setSeverity(rule.getSeverity());
        alert.setTargetType(targetType);
        alert.setTargetKey(targetKey);
        alert.setWarehouseCode(warehouse);
        alert.setTitle(title);
        alert.setDetail(detail);
        alert.setStatus("OPEN");
        alert.setSuggestedAction(rule.getSuggestedAction());
        alertMapper.insert(alert);
    }

    private Map<String, Object> params(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private long number(Object value, long fallback) {
        return value == null ? fallback : Long.parseLong(String.valueOf(value));
    }
}
