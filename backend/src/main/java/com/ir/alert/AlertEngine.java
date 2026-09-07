package com.ir.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
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

    public AlertEngine(
            CtRuleMapper ruleMapper,
            CtAlertMapper alertMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.alertMapper = alertMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
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
        for (ShipmentSnapshot shipment : shipmentMapper.selectList(null)) {
            if (shipment.getPlannedArriveTime() != null
                    && shipment.getPlannedArriveTime().isBefore(now)
                    && !"DELIVERED".equals(shipment.getStatus())
                    && !"CLOSED".equals(shipment.getStatus())) {
                add(rule, "WAYBILL", shipment.getWaybillCode(), null,
                        "运输到达延迟", "计划到达时间已过");
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
