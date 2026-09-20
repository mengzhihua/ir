package com.ir.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.ir.sandbox.service.BalancePolicy;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    private final PurchaseSnapshotMapper purchaseMapper;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;
    private final CostRecordMapper costMapper;
    private final ForecastService forecastService;
    private final BalanceAdvisor balanceAdvisor;
    private final BalancePolicy policy;

    public AlertEngine(
            CtRuleMapper ruleMapper,
            CtAlertMapper alertMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            ExtSnapshotMapper extMapper,
            PurchaseSnapshotMapper purchaseMapper,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper,
            CostRecordMapper costMapper,
            ForecastService forecastService,
            BalanceAdvisor balanceAdvisor,
            BalancePolicy policy) {
        this.ruleMapper = ruleMapper;
        this.alertMapper = alertMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.extMapper = extMapper;
        this.purchaseMapper = purchaseMapper;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
        this.costMapper = costMapper;
        this.forecastService = forecastService;
        this.balanceAdvisor = balanceAdvisor;
        this.policy = policy;
    }

    @Transactional
    public synchronized List<CtAlert> evaluate() {
        Set<String> active = new HashSet<>();
        Set<String> evaluatedRules = new HashSet<>();
        List<CtRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<CtRule>().eq(CtRule::getEnabled, true));
        LocalDateTime now = LocalDateTime.now();
        for (CtRule rule : rules) {
            evaluatedRules.add(rule.getCode());
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
            } else if ("ASN_DELAY".equals(rule.getType())) {
                evaluateAsnDelay(rule, params, active);
            }
        }
        resolveCleared(active, evaluatedRules);
        return all();
    }

    public int openCount() {
        return openCount(null);
    }

    public int openCount(String type) {
        int n = 0;
        for (CtAlert alert : all()) {
            if (!"OPEN".equals(alert.getStatus())) {
                continue;
            }
            if (type != null && !type.equals(alert.getType())) {
                continue;
            }
            n++;
        }
        return n;
    }

    public Map<String, Integer> openCountsByType() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (CtAlert alert : all()) {
            if (!"OPEN".equals(alert.getStatus())) {
                continue;
            }
            String type = alert.getType() == null ? "OTHER" : alert.getType();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
            if ("SAP_LOW_STOCK".equals(alert.getRuleCode())) {
                counts.put("SAP_LOW_STOCK", counts.getOrDefault("SAP_LOW_STOCK", 0) + 1);
            }
        }
        return counts;
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
        List<CtAction> batch = new ArrayList<>();
        batch.add(action);
        if (action != null && "SUCCESS".equals(action.getStatus())
                && "SAP_LOW_STOCK".equals(alert.getRuleCode())) {
            batch.addAll(fanOutPurchase(params, targetKey, id));
        }
        finishBatch(alert, type, batch);
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
            if (terminalStatus(shipment.getStatus())) {
                continue;
            }
            if ((exceptionOnly && !Boolean.TRUE.equals(shipment.getExceptionFlag()))
                    || (!exceptionOnly && shipment.getPlannedArriveTime() == null)) {
                continue;
            }
            if ((exceptionOnly && Boolean.TRUE.equals(shipment.getExceptionFlag()))
                    || (shipment.getPlannedArriveTime() != null
                    && shipment.getPlannedArriveTime().isBefore(now)
                    && !terminalStatus(shipment.getStatus()))) {
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
        int serviceDays = policy.safetyDays();
        int leadDays = policy.replenishLeadDays();
        LocalDate limit = LocalDate.now().plusDays(horizon);
        for (Map<String, Object> row : forecastService.replenish(
                null, null, horizon, serviceDays, leadDays)) {
            java.math.BigDecimal suggest = decimalOf(row.get("suggestQty"));
            if (suggest.signum() <= 0) {
                continue;
            }
            LocalDate stockout = dateOf(row.get("stockoutDate"));
            if (stockout == null) {
                continue;
            }
            LocalDate orderBy = dateOf(row.get("orderByDate"));
            LocalDate due = orderBy == null ? stockout : orderBy;
            if (!due.isAfter(limit)) {
                String sku = String.valueOf(row.get("sku"));
                String warehouse = String.valueOf(row.get("warehouseCode"));
                add(rule, "SKU_WAREHOUSE", sku + "/" + warehouse,
                        warehouse, "预测即将缺货",
                        "低于再订货点 " + row.get("coverDays")
                                + " 天，建议补 " + suggest
                                + "，预计 " + stockout + " 缺货，最晚 " + due
                                + " 下单（提前期 " + leadDays
                                + " 天，保障 " + serviceDays + " 天），"
                                + (balanceAdvisor.costFirst()
                                ? "成本优先只走采购建议、加大批量"
                                : "兼顾时效，采购建议同时仓内补货"),
                        rule.getSuggestedAction(), active);
            }
        }
    }

    private void evaluateInventory(CtRule rule, Set<String> active) {
        Map<String, Map<String, Object>> gaps = new HashMap<>();
        for (Map<String, Object> row : forecastService.replenish(
                null, null, 14, policy.safetyDays(), policy.replenishLeadDays())) {
            gaps.put(com.ir.common.WarehouseCodes.stockKey(
                    String.valueOf(row.get("sku")),
                    String.valueOf(row.get("warehouseCode"))), row);
        }
        for (InventorySnapshot inventory : inventoryMapper.selectList(null)) {
            java.math.BigDecimal available = inventory.getQtyAvailable() == null
                    ? java.math.BigDecimal.ZERO : inventory.getQtyAvailable();
            java.math.BigDecimal inTransit = forecastService.inboundOf(
                    inventory.getSku(), inventory.getWarehouseCode());
            java.math.BigDecimal cover = available.add(inTransit);
            String key = com.ir.common.WarehouseCodes.stockKey(
                    inventory.getSku(), inventory.getWarehouseCode());
            Map<String, Object> row = gaps.get(key);
            java.math.BigDecimal demand = row == null
                    ? java.math.BigDecimal.ZERO : decimalOf(row.get("forecastDemand"));
            boolean below;
            String detail;
            if (demand.signum() > 0) {
                java.math.BigDecimal suggest = decimalOf(row.get("suggestQty"));
                below = suggest.signum() > 0;
                detail = "可用+在途低于再订货点 " + row.get("coverDays")
                        + " 天（可覆盖 " + row.get("onHandDays")
                        + "），建议补 " + suggest + "，"
                        + (balanceAdvisor.costFirst()
                        ? "成本优先加大采购批量"
                        : "兼顾时效，采购建议同时仓内补货");
            } else {
                java.math.BigDecimal safety = inventory.getSafetyQty() == null
                        ? java.math.BigDecimal.ZERO : inventory.getSafetyQty();
                below = cover.compareTo(safety) < 0;
                detail = "可用+在途仍低于安全库存（在途 "
                        + inTransit + "），"
                        + (balanceAdvisor.costFirst()
                        ? "成本优先加大采购批量"
                        : "兼顾时效，采购建议同时仓内补货");
            }
            if (!below) {
                continue;
            }
            add(rule, "SKU_WAREHOUSE", key, inventory.getWarehouseCode(),
                    "低库存", detail, rule.getSuggestedAction(), active);
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

    private void evaluateAsnDelay(CtRule rule, Map<String, Object> params, Set<String> active) {
        long days = number(params.get("days"), 0L);
        LocalDate limit = LocalDate.now().minusDays(days);
        for (PurchaseSnapshot doc : purchaseMapper.selectList(new LambdaQueryWrapper<PurchaseSnapshot>()
                .eq(PurchaseSnapshot::getDocType, "ASN"))) {
            if (Arrays.asList("RECEIVED", "POSTED", "CANCELLED").contains(doc.getStatus())) {
                continue;
            }
            boolean late = "DELAYED".equals(doc.getStatus())
                    || (doc.getExpectedDate() != null && !doc.getExpectedDate().isAfter(limit));
            if (!late) {
                continue;
            }
            String target = doc.getRefCode() == null || doc.getRefCode().trim().isEmpty()
                    ? doc.getCode() : doc.getRefCode();
            add(rule, "ASN", target, doc.getPlantCode(),
                    "供应商到货延误",
                    (doc.getCode() == null ? target : doc.getCode())
                            + " 预计 " + doc.getExpectedDate() + " 到货已延误",
                    rule.getSuggestedAction(), active);
        }
        for (ExtSnapshot row : extMapper.selectList(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, "SRM")
                .eq(ExtSnapshot::getDataType, "ASN"))) {
            if (!"DELAYED".equals(row.getStatus())) {
                continue;
            }
            Map<String, Object> extra = params(row.getExtraJson());
            String target = firstNonBlank(
                    string(extra.get("poCode")),
                    string(extra.get("refCode")),
                    row.getBizKey());
            add(rule, "ASN", target, row.getPlantCode(),
                    "供应商到货延误",
                    (row.getTitle() == null ? row.getBizKey() : row.getTitle()) + " 状态 DELAYED",
                    rule.getSuggestedAction(), active);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
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
        CtAlert existing = findAlert(rule.getCode(), targetKey, warehouse, "OPEN", false);
        String suggested = suggestedAction == null || suggestedAction.trim().isEmpty()
                ? rule.getSuggestedAction() : suggestedAction;
        if (existing != null) {
            boolean actionChanged = suggested != null
                    && !suggested.equals(existing.getSuggestedAction());
            boolean detailChanged = detail != null && !detail.equals(existing.getDetail());
            boolean migrated = migrateStockKey(existing, targetType, targetKey, warehouse);
            if (actionChanged || detailChanged || migrated) {
                existing.setSuggestedAction(suggested);
                existing.setDetail(detail);
                alertMapper.updateById(existing);
            }
            return;
        }
        CtAlert handled = findAlert(rule.getCode(), targetKey, warehouse, "RESOLVED", true);
        if (handled != null) {
            if (migrateStockKey(handled, targetType, targetKey, warehouse)) {
                alertMapper.updateById(handled);
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

    private CtAction finishBatch(CtAlert alert, String suggested, List<CtAction> batch) {
        alert.setSuggestedAction(suggested);
        CtAction primary = firstAction(batch);
        if (allSuccess(batch)) {
            alert.setActionId(primary == null ? null : primary.getId());
            close(alert);
            return primary;
        }
        alert.setActionId(null);
        alertMapper.updateById(alert);
        return primary;
    }

    private boolean allSuccess(List<CtAction> batch) {
        if (batch == null || batch.isEmpty()) {
            return false;
        }
        for (CtAction action : batch) {
            if (action == null || !"SUCCESS".equals(action.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private CtAction firstAction(List<CtAction> batch) {
        if (batch == null) {
            return null;
        }
        for (CtAction action : batch) {
            if (action != null) {
                return action;
            }
        }
        return null;
    }

    private void resolveCleared(Set<String> active, Set<String> evaluatedRules) {
        if (evaluatedRules == null || evaluatedRules.isEmpty()) {
            return;
        }
        List<CtAlert> rows = alertMapper.selectList(
                new LambdaQueryWrapper<CtAlert>()
                        .in(CtAlert::getStatus, Arrays.asList("OPEN", "RESOLVED"))
                        .in(CtAlert::getRuleCode, evaluatedRules));
        for (CtAlert alert : rows) {
            if (active.contains(activeKey(alert.getRuleCode(), alert.getTargetKey()))
                    || legacyStockActive(alert, active)) {
                continue;
            }
            if ("OPEN".equals(alert.getStatus())) {
                close(alert);
            } else if (alert.getActionId() != null) {
                alertMapper.update(null, new LambdaUpdateWrapper<CtAlert>()
                        .eq(CtAlert::getId, alert.getId())
                        .set(CtAlert::getActionId, null));
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

    private boolean terminalStatus(String status) {
        return "DELIVERED".equals(status)
                || "CLOSED".equals(status)
                || "CANCELLED".equals(status);
    }

    private String activeKey(String ruleCode, String targetKey) {
        return (ruleCode == null ? "" : ruleCode) + "\0"
                + (targetKey == null ? "" : targetKey);
    }

    private CtAlert findAlert(
            String ruleCode,
            String targetKey,
            String warehouse,
            String status,
            boolean handled) {
        LambdaQueryWrapper<CtAlert> query = new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getRuleCode, ruleCode)
                .eq(CtAlert::getTargetKey, targetKey)
                .eq(CtAlert::getStatus, status);
        if (handled) {
            query.isNotNull(CtAlert::getActionId).orderByDesc(CtAlert::getId);
        }
        CtAlert exact = alertMapper.selectOne(query.last("LIMIT 1"));
        if (exact != null) {
            return exact;
        }
        return findLegacyStockAlert(ruleCode, targetKey, warehouse, status, handled);
    }

    private CtAlert findLegacyStockAlert(
            String ruleCode,
            String targetKey,
            String warehouse,
            String status,
            boolean handled) {
        if (targetKey == null || !targetKey.contains("/")) {
            return null;
        }
        String[] parts = targetKey.split("/", 2);
        String sku = parts[0];
        String site = parts.length > 1 ? parts[1] : warehouse;
        LambdaQueryWrapper<CtAlert> query = new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getRuleCode, ruleCode)
                .eq(CtAlert::getTargetKey, sku)
                .eq(CtAlert::getStatus, status);
        if (handled) {
            query.isNotNull(CtAlert::getActionId).orderByDesc(CtAlert::getId);
        }
        CtAlert legacy = alertMapper.selectOne(query.last("LIMIT 1"));
        if (legacy == null) {
            return null;
        }
        if (legacy.getWarehouseCode() != null && !legacy.getWarehouseCode().trim().isEmpty()
                && site != null && !site.equals(legacy.getWarehouseCode())) {
            return null;
        }
        return legacy;
    }

    private boolean migrateStockKey(
            CtAlert existing,
            String targetType,
            String targetKey,
            String warehouse) {
        boolean changed = false;
        if (targetKey != null && !targetKey.equals(existing.getTargetKey())) {
            existing.setTargetKey(targetKey);
            changed = true;
        }
        if (targetType != null && !targetType.equals(existing.getTargetType())) {
            existing.setTargetType(targetType);
            changed = true;
        }
        if (warehouse != null && !warehouse.equals(existing.getWarehouseCode())) {
            existing.setWarehouseCode(warehouse);
            changed = true;
        }
        return changed;
    }

    private boolean legacyStockActive(CtAlert alert, Set<String> active) {
        String key = alert.getTargetKey();
        if (key == null || key.contains("/")) {
            return false;
        }
        if (alert.getWarehouseCode() != null && !alert.getWarehouseCode().trim().isEmpty()) {
            return active.contains(activeKey(
                    alert.getRuleCode(), key + "/" + alert.getWarehouseCode()));
        }
        String prefix = activeKey(alert.getRuleCode(), key + "/");
        for (String item : active) {
            if (item.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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

    private LocalDate dateOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        return LocalDate.parse(text);
    }

    private java.math.BigDecimal decimalOf(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return java.math.BigDecimal.ZERO;
        }
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

    private java.math.BigDecimal ropSuggestQty(String sku, String warehouse) {
        java.util.List<Map<String, Object>> rows = forecastService.replenish(
                warehouse, sku, 14, policy.safetyDays(), policy.replenishLeadDays());
        if (rows == null || rows.isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        if (decimalOf(rows.get(0).get("forecastDemand")).signum() <= 0) {
            return java.math.BigDecimal.ZERO;
        }
        return decimalOf(rows.get(0).get("suggestQty"));
    }

    private java.math.BigDecimal suggestQty(java.math.BigDecimal onHand) {
        java.math.BigDecimal target = java.math.BigDecimal.TEN;
        if (onHand == null) {
            return target;
        }
        java.math.BigDecimal gap = target.subtract(onHand);
        return gap.signum() > 0 ? gap : java.math.BigDecimal.ONE;
    }

    private List<CtAction> fanOutPurchase(Map<String, Object> params, String sku, Long alertId) {
        List<CtAction> rows = new ArrayList<>();
        rows.add(dispatch("SRM_PURCHASE_SUGGEST", sku, params, alertId));
        Map<String, Object> oa = new LinkedHashMap<>(params);
        oa.put("definitionCode", "GENERAL");
        oa.put("title", "采购补货审批 " + sku);
        oa.put("businessType", "SAP_PR");
        oa.put("businessId", sku);
        rows.add(dispatch("OA_START_WORKFLOW", sku, oa, alertId));
        String warehouse = String.valueOf(params.getOrDefault("warehouseCode", "WH-SH"));
        Map<String, Object> wms = new LinkedHashMap<>();
        wms.put("warehouseCode", warehouse);
        rows.add(dispatch("WMS_REPLENISH", warehouse, wms, alertId));
        return rows;
    }

    private CtAction executeOverrun(CtAlert alert) {
        java.util.List<BalanceAdvisor.Advice> advice = balanceAdvisor.adviseOverrun(
                alert.getWarehouseCode(),
                shipmentMapper.selectList(null),
                orderMapper.selectList(null));
        if (advice.isEmpty()) {
            return null;
        }
        List<CtAction> batch = new ArrayList<>();
        for (BalanceAdvisor.Advice item : advice) {
            batch.add(dispatch(item.getType(), item.getTargetKey(),
                    item.params(), alert.getId(), item.getExpectedSaving()));
        }
        return finishBatch(alert, BalanceAdvisor.SWITCH, batch);
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
        java.math.BigDecimal gap = ropSuggestQty(sku, warehouse);
        if (gap.signum() <= 0) {
            InventorySnapshot inventory = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<InventorySnapshot>()
                            .eq(InventorySnapshot::getSku, sku)
                            .eq(warehouse != null && !warehouse.trim().isEmpty(),
                                    InventorySnapshot::getWarehouseCode, warehouse)
                            .last("LIMIT 1"));
            if (inventory != null && inventory.getSafetyQty() != null
                    && inventory.getQtyAvailable() != null) {
                java.math.BigDecimal inTransit = forecastService.inboundOf(
                        inventory.getSku(), inventory.getWarehouseCode());
                gap = inventory.getSafetyQty()
                        .subtract(inventory.getQtyAvailable())
                        .subtract(inTransit)
                        .max(java.math.BigDecimal.ONE);
            } else {
                gap = java.math.BigDecimal.ONE;
            }
        }
        java.util.List<BalanceAdvisor.Advice> advice = balanceAdvisor.adviseStockout(
                sku, warehouse, gap);
        if (advice.isEmpty()) {
            return null;
        }
        List<CtAction> batch = new ArrayList<>();
        for (BalanceAdvisor.Advice item : advice) {
            batch.add(dispatch(item.getType(), item.getTargetKey(),
                    item.params(), alert.getId()));
        }
        CtAction primary = firstAction(batch);
        return finishBatch(alert,
                primary == null ? alert.getSuggestedAction() : primary.getType(),
                batch);
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
