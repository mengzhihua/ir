package com.ir.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.alert.entity.CtAlert;
import com.ir.alert.entity.CtRule;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.alert.mapper.CtRuleMapper;
import com.ir.common.CodeGenerator;
import com.ir.cost.service.CostService;
import com.ir.forecast.service.ForecastService;
import com.ir.sandbox.service.BalanceAdvisor;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AlertEngine {
    private final CtRuleMapper ruleMapper;
    private final CtAlertMapper alertMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final ExtSnapshotMapper extMapper;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;
    private final CostRecordMapper costMapper;
    private final ForecastService forecastService;
    private final BalanceAdvisor balanceAdvisor;

    public AlertEngine(
            CtRuleMapper ruleMapper,
            CtAlertMapper alertMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            ExtSnapshotMapper extMapper,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper,
            CostRecordMapper costMapper,
            ForecastService forecastService,
            BalanceAdvisor balanceAdvisor) {
        this.ruleMapper = ruleMapper;
        this.alertMapper = alertMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.extMapper = extMapper;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
        this.costMapper = costMapper;
        this.forecastService = forecastService;
        this.balanceAdvisor = balanceAdvisor;
    }

    @Transactional
    public synchronized List<CtAlert> evaluate() {
        Set<String> active = new HashSet<>();
        List<CtRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<CtRule>().eq(CtRule::getEnabled, true));
        LocalDateTime now = LocalDateTime.now();
        for (CtRule rule : rules) {
            Map<String, Object> params = params(rule.getParamsJson());
            if ("ORDER_STUCK".equals(rule.getType())) {
                evaluateOrders(rule, params, now, active);
            } else if ("WMS_STUCK".equals(rule.getType())) {
                evaluateWms(rule, params, now, active);
            } else if ("TMS_DELAY".equals(rule.getType())) {
                evaluateShipments(rule, now, active);
            } else if ("LOW_STOCK".equals(rule.getType())) {
                evaluateInventory(rule, active);
            } else if ("COST_OVERRUN".equals(rule.getType())) {
                evaluateCost(rule, params, active);
            } else if ("FORECAST_STOCKOUT".equals(rule.getType())) {
                evaluateForecast(rule, params, active);
            } else if ("EXT_STATUS".equals(rule.getType())) {
                evaluateExt(rule, params, active);
            }
        }
        resolveCleared(active);
        return all();
    }

    public Page<CtAlert> page(
            String status,
            String severity,
            String type,
            long current,
            long size) {
        LambdaQueryWrapper<CtAlert> query = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            query.eq(CtAlert::getStatus, status);
        }
        if (severity != null && !severity.trim().isEmpty()) {
            query.eq(CtAlert::getSeverity, severity);
        }
        if (type != null && !type.trim().isEmpty()) {
            query.eq(CtAlert::getType, type);
        }
        query.orderByDesc(CtAlert::getCreatedAt);
        return alertMapper.selectPage(new Page<>(current, size), query);
    }

    public List<CtAlert> all() {
        return alertMapper.selectList(
                new LambdaQueryWrapper<CtAlert>()
                        .orderByDesc(CtAlert::getCreatedAt));
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
        if (alert.getSuggestedAction() == null || alert.getSuggestedAction().trim().isEmpty()) {
            return null;
        }
        if ("COST_OVERRUN".equals(alert.getType())) {
            return executeOverrun(alert);
        }
        if ("LOW_STOCK".equals(alert.getType()) || "FORECAST_STOCKOUT".equals(alert.getType())) {
            return executeStockout(alert);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        fillFromExt(alert, params);
        fillFromShipment(alert, params);
        String type = alert.getSuggestedAction();
        String targetKey = actionKey(alert, params);
        BalanceAdvisor.Advice advice = null;
        if ("TMS_DELAY".equals(alert.getType())) {
            ShipmentSnapshot shipment = shipmentOf(alert.getTargetKey());
            advice = balanceAdvisor.adviseDelay(shipment);
            if (advice != null) {
                type = advice.getType();
                targetKey = advice.getTargetKey();
                params.putAll(advice.params());
            }
        } else if ("ORDER_STUCK".equals(alert.getType())) {
            OrderSnapshot order = orderOf(alert.getTargetKey());
            advice = balanceAdvisor.adviseStuckOrder(
                    order, alert.getRuleCode());
            if (advice != null) {
                type = advice.getType();
                targetKey = advice.getTargetKey();
                params.putAll(advice.params());
            }
        } else if ("WMS_STUCK".equals(alert.getType())) {
            WmsOrderSnapshot outbound = wmsOf(alert.getTargetKey());
            OrderSnapshot order = outbound == null ? null : orderOf(outbound.getExternalNo());
            advice = balanceAdvisor.adviseWmsStuck(outbound, order);
            if (advice != null) {
                type = advice.getType();
                targetKey = advice.getTargetKey();
                params.putAll(advice.params());
            }
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("targetKey", targetKey);
        request.put("params", params);
        request.put("alertId", id);
        request.put("expectedSaving", savingOf(advice, type, targetKey, params));
        CtAction action = actions.createAndExecute(request);
        if (action != null && "SUCCESS".equals(action.getStatus())
                && "SAP_LOW_STOCK".equals(alert.getRuleCode())) {
            fanOutPurchase(params, targetKey, id);
        }
        finishExecute(alert, type, action);
        return action;
    }

    private void evaluateOrders(
            CtRule rule, Map<String, Object> params, LocalDateTime now, Set<String> active) {
        String expectedStatus = String.valueOf(params.get("status"));
        long threshold = number(params.get("hours"), 4L);
        for (OrderSnapshot order : orderMapper.selectList(null)) {
            if (expectedStatus.equals(order.getStatus())
                    && order.getOrderTime() != null
                    && Duration.between(order.getOrderTime(), now).toHours() > threshold) {
                BalanceAdvisor.Advice advice = balanceAdvisor.adviseStuckOrder(
                        order, rule.getCode());
                String suggested = advice == null
                        ? rule.getSuggestedAction() : advice.getType();
                String detail = expectedStatus + " 超过 " + threshold + " 小时";
                if (advice != null) {
                    detail = detail + "，按成本/效率权重建议 " + advice.getType();
                }
                add(rule, "ORDER", order.getOrderNo(), order.getWarehouseCode(),
                        "订单卡单", detail, suggested, active);
            }
        }
    }

    private void evaluateWms(
            CtRule rule, Map<String, Object> params, LocalDateTime now, Set<String> active) {
        long threshold = number(params.get("hours"), 6L);
        java.util.Set<String> statuses = stuckStatuses(params.get("status"));
        for (WmsOrderSnapshot outbound : wmsMapper.selectList(null)) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, outbound.getExternalNo()));
            if (order != null && statuses.contains(outbound.getStatus())
                    && order.getOrderTime() != null
                    && Duration.between(order.getOrderTime(), now).toHours() > threshold) {
                BalanceAdvisor.Advice advice = balanceAdvisor.adviseWmsStuck(outbound, order);
                String suggested = advice == null
                        ? rule.getSuggestedAction() : advice.getType();
                String detail = outbound.getStatus() + " 超过 " + threshold + " 小时";
                if (advice != null) {
                    detail = detail + "，按成本/效率权重建议 " + advice.getType();
                }
                add(rule, "ORDER", outbound.getCode(), outbound.getWarehouseCode(),
                        "WMS 拣货卡单", detail, suggested, active);
            }
        }
    }

    private void evaluateShipments(CtRule rule, LocalDateTime now, Set<String> active) {
        boolean exceptionOnly = String.valueOf(rule.getParamsJson())
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
                String suggested = rule.getSuggestedAction();
                String detail = exceptionOnly ? "运单异常，先拉轨迹" : "计划到达时间已过";
                if (!exceptionOnly) {
                    BalanceAdvisor.Advice advice = balanceAdvisor.adviseDelay(shipment);
                    if (advice != null) {
                        suggested = advice.getType();
                        if (BalanceAdvisor.SWITCH.equals(advice.getType())) {
                            detail = "计划到达时间已过，成本/效率权衡后建议换 "
                                    + advice.getCarrierCode();
                        } else {
                            detail = "计划到达时间已过，当前承运商已较优，建议追轨迹";
                        }
                    }
                }
                add(rule, "WAYBILL", shipment.getWaybillCode(),
                        com.ir.common.WarehouseCodes.toOms(shipment.getFromSiteCode()),
                        "运输到达延迟", detail, suggested, active);
            }
        }
    }

    private void evaluateCost(CtRule rule, Map<String, Object> params, Set<String> active) {
        LocalDate from = LocalDate.now().minusDays(number(
                params.get("days"), 7L) - 1L);
        java.util.Map<String, java.math.BigDecimal> amountByWarehouse =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> ordersByWarehouse =
                new java.util.LinkedHashMap<>();
        for (CostRecord row : CostService.preferSettlement(costMapper.selectList(
                new LambdaQueryWrapper<CostRecord>()
                        .ge(CostRecord::getBizDate, from)))) {
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
        java.util.List<ShipmentSnapshot> shipments = shipmentMapper.selectList(null);
        java.util.List<OrderSnapshot> orders = orderMapper.selectList(null);
        for (String warehouse : amountByWarehouse.keySet()) {
            int count = ordersByWarehouse.get(warehouse).size();
            java.math.BigDecimal perOrder = amountByWarehouse.get(warehouse)
                    .divide(java.math.BigDecimal.valueOf(Math.max(1, count)),
                            4, java.math.RoundingMode.HALF_UP);
            if (perOrder.compareTo(threshold) > 0) {
                java.util.List<BalanceAdvisor.Advice> advice = balanceAdvisor.adviseOverrun(
                        warehouse, shipments, orders);
                String recommended = advice.isEmpty()
                        ? balanceAdvisor.pickCheaperForOverrun("SF")
                        : advice.get(0).getCarrierCode();
                add(rule, "WAREHOUSE", warehouse, warehouse,
                        "仓库成本超标", "近 " + params.getOrDefault(
                                "days", 7) + " 天单均成本 " + perOrder
                                + "，按成本/效率权重建议换承运商 " + recommended,
                        rule.getSuggestedAction(), active);
            }
        }
    }

    private void evaluateForecast(CtRule rule, Map<String, Object> params, Set<String> active) {
        int horizon = (int) number(params.get("horizon"),
                number(params.get("days"), 14L));
        int serviceDays = (int) number(params.get("serviceDays"), 3L);
        LocalDate limit = LocalDate.now().plusDays(horizon);
        for (Map<String, Object> row : forecastService.replenish(
                null, null, horizon, serviceDays)) {
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
                        "预计 " + stockout + " 缺货，"
                                + (balanceAdvisor.costFirst()
                                ? "成本优先只走采购建议、加大批量"
                                : "兼顾时效，采购建议同时仓内补货"),
                        rule.getSuggestedAction(), active);
            }
        }
    }

    private void evaluateInventory(CtRule rule, Set<String> active) {
        for (InventorySnapshot inventory : inventoryMapper.selectList(null)) {
            if (inventory.getQtyAvailable().compareTo(inventory.getSafetyQty()) < 0) {
                add(rule, "SKU", inventory.getSku(), inventory.getWarehouseCode(),
                        "低库存", "可用库存低于安全库存，"
                                + (balanceAdvisor.costFirst()
                                ? "成本优先加大采购批量"
                                : "兼顾时效，采购建议同时仓内补货"),
                        rule.getSuggestedAction(), active);
            }
        }
    }

    private void evaluateExt(CtRule rule, Map<String, Object> params, Set<String> active) {
        String system = String.valueOf(params.getOrDefault("system", ""));
        String dataType = String.valueOf(params.getOrDefault("dataType", ""));
        String status = String.valueOf(params.getOrDefault("status", ""));
        for (ExtSnapshot row : extMapper.selectList(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, system)
                .eq(ExtSnapshot::getDataType, dataType))) {
            if (status.equals(row.getStatus())) {
                add(rule, dataType, row.getBizKey(), row.getPlantCode(),
                        rule.getName(), (row.getTitle() == null ? row.getBizKey() : row.getTitle())
                                + " 状态 " + row.getStatus(),
                        rule.getSuggestedAction(), active);
            }
        }
    }

    private void add(
            CtRule rule,
            String targetType,
            String targetKey,
            String warehouse,
            String title,
            String detail,
            String suggestedAction,
            Set<String> active) {
        if (active != null) {
            active.add(activeKey(rule.getCode(), targetKey));
        }
        CtAlert existing = alertMapper.selectOne(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getRuleCode, rule.getCode())
                .eq(CtAlert::getTargetKey, targetKey)
                .eq(CtAlert::getStatus, "OPEN"));
        String suggested = suggestedAction == null || suggestedAction.trim().isEmpty()
                ? rule.getSuggestedAction() : suggestedAction;
        if (existing != null) {
            boolean actionChanged = suggested != null
                    && !suggested.equals(existing.getSuggestedAction());
            boolean detailChanged = detail != null && !detail.equals(existing.getDetail());
            if (actionChanged || detailChanged) {
                existing.setSuggestedAction(suggested);
                existing.setDetail(detail);
                alertMapper.updateById(existing);
            }
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
        alert.setSuggestedAction(suggested);
        alertMapper.insert(alert);
    }

    private void finishExecute(CtAlert alert, String suggested, CtAction action) {
        alert.setSuggestedAction(suggested);
        alert.setActionId(action == null ? null : action.getId());
        if (action != null && "SUCCESS".equals(action.getStatus())) {
            close(alert);
            return;
        }
        alertMapper.updateById(alert);
    }

    private void resolveCleared(Set<String> active) {
        List<CtAlert> open = alertMapper.selectList(
                new LambdaQueryWrapper<CtAlert>().eq(CtAlert::getStatus, "OPEN"));
        for (CtAlert alert : open) {
            if (!active.contains(activeKey(alert.getRuleCode(), alert.getTargetKey()))) {
                close(alert);
            }
        }
    }

    private void close(CtAlert alert) {
        if (alert == null || "RESOLVED".equals(alert.getStatus())
                || "IGNORED".equals(alert.getStatus())) {
            return;
        }
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    private String activeKey(String ruleCode, String targetKey) {
        return (ruleCode == null ? "" : ruleCode) + "\0"
                + (targetKey == null ? "" : targetKey);
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

    private void fillFromExt(CtAlert alert, Map<String, Object> params) {
        if (alert.getTargetKey() == null) {
            return;
        }
        ExtSnapshot row = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getBizKey, alert.getTargetKey())
                .eq(alert.getTargetType() != null && !alert.getTargetType().trim().isEmpty(),
                        ExtSnapshot::getDataType, alert.getTargetType())
                .last("LIMIT 1"));
        if (row == null) {
            return;
        }
        if (row.getSku() != null && !row.getSku().trim().isEmpty()) {
            params.put("sku", row.getSku());
            params.put("matnr", row.getSku());
        }
        if (row.getPlantCode() != null && !row.getPlantCode().trim().isEmpty()) {
            params.put("plantCode", row.getPlantCode());
            params.put("werks", row.getPlantCode());
        }
        params.put("qty", suggestQty(row.getQty()));
        params.put("warehouseCode", com.ir.common.WarehouseCodes.fromPlant(row.getPlantCode()));
        if (row.getTitle() != null) {
            params.put("title", row.getTitle());
        }
    }

    private String actionKey(CtAlert alert, Map<String, Object> params) {
        String type = alert.getSuggestedAction();
        if ("SAP_CREATE_PR".equals(type) || "SRM_PURCHASE_SUGGEST".equals(type)) {
            Object sku = params.get("sku");
            if (sku != null && !String.valueOf(sku).trim().isEmpty()) {
                return String.valueOf(sku).trim();
            }
            String key = alert.getTargetKey();
            if (key != null && key.contains("/")) {
                return key.split("/")[0];
            }
        }
        return alert.getTargetKey();
    }

    private java.math.BigDecimal suggestQty(java.math.BigDecimal onHand) {
        java.math.BigDecimal target = java.math.BigDecimal.TEN;
        if (onHand == null) {
            return target;
        }
        java.math.BigDecimal gap = target.subtract(onHand);
        return gap.signum() > 0 ? gap : java.math.BigDecimal.ONE;
    }

    private void fanOutPurchase(Map<String, Object> params, String sku, Long alertId) {
        dispatch("SRM_PURCHASE_SUGGEST", sku, params, alertId);
        Map<String, Object> oa = new LinkedHashMap<>(params);
        oa.put("definitionCode", "GENERAL");
        oa.put("title", "采购补货审批 " + sku);
        oa.put("businessType", "SAP_PR");
        oa.put("businessId", sku);
        dispatch("OA_START_WORKFLOW", sku, oa, alertId);
        String warehouse = String.valueOf(params.getOrDefault("warehouseCode", "WH-SH"));
        Map<String, Object> wms = new LinkedHashMap<>();
        wms.put("warehouseCode", warehouse);
        dispatch("WMS_REPLENISH", warehouse, wms, alertId);
    }

    private CtAction executeOverrun(CtAlert alert) {
        java.util.List<BalanceAdvisor.Advice> advice = balanceAdvisor.adviseOverrun(
                alert.getWarehouseCode(),
                shipmentMapper.selectList(null),
                orderMapper.selectList(null));
        if (advice.isEmpty()) {
            return null;
        }
        CtAction primary = null;
        for (BalanceAdvisor.Advice item : advice) {
            CtAction action = dispatch(item.getType(), item.getTargetKey(),
                    item.params(), alert.getId(), item.getExpectedSaving());
            if (primary == null) {
                primary = action;
            }
        }
        finishExecute(alert, BalanceAdvisor.SWITCH, primary);
        return primary;
    }

    private CtAction executeStockout(CtAlert alert) {
        String sku = alert.getTargetKey();
        String warehouse = alert.getWarehouseCode();
        if (sku != null && sku.contains("/")) {
            String[] parts = sku.split("/", 2);
            sku = parts[0];
            if (warehouse == null || warehouse.trim().isEmpty()) {
                warehouse = parts[1];
            }
        }
        java.math.BigDecimal gap = java.math.BigDecimal.TEN;
        InventorySnapshot inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<InventorySnapshot>()
                        .eq(InventorySnapshot::getSku, sku)
                        .eq(warehouse != null && !warehouse.trim().isEmpty(),
                                InventorySnapshot::getWarehouseCode, warehouse)
                        .last("LIMIT 1"));
        if (inventory != null && inventory.getSafetyQty() != null
                && inventory.getQtyAvailable() != null) {
            gap = inventory.getSafetyQty().subtract(inventory.getQtyAvailable())
                    .max(java.math.BigDecimal.ONE);
        }
        java.util.List<BalanceAdvisor.Advice> advice = balanceAdvisor.adviseStockout(
                sku, warehouse, gap);
        if (advice.isEmpty()) {
            return null;
        }
        CtAction primary = null;
        for (BalanceAdvisor.Advice item : advice) {
            CtAction action = dispatch(item.getType(), item.getTargetKey(),
                    item.params(), alert.getId());
            if (primary == null) {
                primary = action;
            }
        }
        finishExecute(alert,
                primary == null ? alert.getSuggestedAction() : primary.getType(),
                primary);
        return primary;
    }

    private void fillFromShipment(CtAlert alert, Map<String, Object> params) {
        if (!"WAYBILL".equals(alert.getTargetType())) {
            return;
        }
        ShipmentSnapshot shipment = shipmentOf(alert.getTargetKey());
        if (shipment == null) {
            return;
        }
        params.put("waybillCode", shipment.getWaybillCode());
        if (shipment.getCarrierCode() != null) {
            params.putIfAbsent("carrierCode",
                    com.ir.common.CarrierCodes.toTms(shipment.getCarrierCode()));
        }
    }

    private ShipmentSnapshot shipmentOf(String waybill) {
        if (waybill == null || waybill.trim().isEmpty()) {
            return null;
        }
        return shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentSnapshot>()
                .eq(ShipmentSnapshot::getWaybillCode, waybill)
                .last("LIMIT 1"));
    }

    private OrderSnapshot orderOf(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                .eq(OrderSnapshot::getOrderNo, orderNo)
                .last("LIMIT 1"));
    }

    private WmsOrderSnapshot wmsOf(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        WmsOrderSnapshot outbound = wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                .eq(WmsOrderSnapshot::getCode, code)
                .last("LIMIT 1"));
        if (outbound != null) {
            return outbound;
        }
        return wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                .eq(WmsOrderSnapshot::getExternalNo, code)
                .last("LIMIT 1"));
    }

    private CtAction dispatch(String type, String targetKey, Map<String, Object> params, Long alertId) {
        return dispatch(type, targetKey, params, alertId, null);
    }

    private CtAction dispatch(
            String type,
            String targetKey,
            Map<String, Object> params,
            Long alertId,
            java.math.BigDecimal expectedSaving) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("targetKey", targetKey);
        request.put("params", params);
        request.put("alertId", alertId);
        request.put("expectedSaving", savingOf(null, type, targetKey, params, expectedSaving));
        return actions.createAndExecute(request);
    }

    private java.math.BigDecimal savingOf(
            BalanceAdvisor.Advice advice,
            String type,
            String targetKey,
            Map<String, Object> params) {
        return savingOf(advice, type, targetKey, params, null);
    }

    private java.math.BigDecimal savingOf(
            BalanceAdvisor.Advice advice,
            String type,
            String targetKey,
            Map<String, Object> params,
            java.math.BigDecimal explicit) {
        if (explicit != null) {
            return explicit;
        }
        if (advice != null && advice.getExpectedSaving() != null) {
            return advice.getExpectedSaving();
        }
        if (BalanceAdvisor.SWITCH.equals(type) && params != null && params.get("carrierCode") != null) {
            ShipmentSnapshot shipment = shipmentOf(targetKey);
            if (shipment != null) {
                return BalanceAdvisor.freightSaving(
                        shipment.getCarrierCode(),
                        String.valueOf(params.get("carrierCode")),
                        shipment.getFreightAmount());
            }
        }
        return java.math.BigDecimal.ZERO;
    }

    private java.util.Set<String> stuckStatuses(Object value) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        if (value == null || String.valueOf(value).trim().isEmpty()
                || "null".equals(String.valueOf(value))) {
            out.add("NEW");
            out.add("PART_ALLOCATED");
            out.add("PICKING");
            return out;
        }
        for (String part : String.valueOf(value).split(",")) {
            if (!part.trim().isEmpty()) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
