package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.common.CodeGenerator;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import com.ir.system.CurrentUser;
import com.ir.system.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActionService {
    private final CtActionMapper actionMapper;
    private final CtSystemMapper systemMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final ClientFactory clients;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public ActionService(
            CtActionMapper actionMapper,
            CtSystemMapper systemMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            ClientFactory clients,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.systemMapper = systemMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.clients = clients;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CtAction createAndExecute(Map<String, Object> request) {
        CtAction action = create(request);
        if ("SRM".equals(action.getTargetSystem())) {
            action.setResult("SRM 未接入，已生成采购建议");
            actionMapper.updateById(action);
            return action;
        }
        try {
            execute(action, read(action.getParamsJson()));
        } catch (Exception ex) {
            action.setStatus("FAILED");
            action.setResult(ex.getMessage());
            action.setExecutedAt(LocalDateTime.now());
            actionMapper.updateById(action);
        }
        return actionMapper.selectById(action.getId());
    }

    @Transactional
    public CtAction createPending(Map<String, Object> request) {
        return create(request);
    }

    private CtAction create(Map<String, Object> request) {
        String type = String.valueOf(request.get("type"));
        String targetKey = String.valueOf(request.get("targetKey"));
        Map<String, Object> params = request.get("params") instanceof Map
                ? objectMapper.convertValue(request.get("params"),
                new TypeReference<Map<String, Object>>() {
                })
                : new LinkedHashMap<>();

        CtAction action = new CtAction();
        action.setActionNo(codes.next("ACT"));
        action.setType(type);
        action.setTargetKey(targetKey);
        action.setTargetSystem(systemFor(type));
        action.setParams(write(params));
        action.setStatus("PENDING");
        action.setOperator(operator());
        action.setExpectedSaving(decimal(request.get("expectedSaving")));
        if (request.get("alertId") != null) {
            action.setAlertId(Long.valueOf(String.valueOf(request.get("alertId"))));
        }
        actionMapper.insert(action);
        return action;
    }

    private void execute(CtAction action, Map<String, Object> params) {
        try {
            CtSystem system = systemMapper.selectOne(
                    new LambdaQueryWrapper<CtSystem>()
                            .eq(CtSystem::getCode, action.getTargetSystem()));
            ActionCommand command = new ActionCommand();
            command.setType(action.getType());
            command.setTargetKey(action.getTargetKey());
            command.setParams(params);
            if ("OMS".equals(action.getTargetSystem())) {
                clients.oms(system).execute(command);
            } else if ("WMS".equals(action.getTargetSystem())) {
                clients.wms(system).execute(command);
            } else {
                clients.tms(system).execute(command);
            }
            mutateSnapshot(action, params);
            action.setStatus("SUCCESS");
            action.setResult("指令执行成功");
        } catch (Exception ex) {
            action.setStatus("FAILED");
            action.setResult(ex.getMessage());
        }
        action.setExecutedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    public CtAction retry(Long id) {
        CtAction original = actionMapper.selectById(id);
        if (original == null) {
            return null;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", original.getType());
        request.put("targetKey", original.getTargetKey());
        request.put("params", read(original.getParamsJson()));
        request.put("alertId", original.getAlertId());
        return createAndExecute(request);
    }

    public Page<CtAction> page(
            String type,
            String status,
            String targetKey,
            long current,
            long size) {
        LambdaQueryWrapper<CtAction> query = new LambdaQueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            query.eq(CtAction::getType, type);
        }
        if (status != null && !status.trim().isEmpty()) {
            query.eq(CtAction::getStatus, status);
        }
        if (targetKey != null && !targetKey.trim().isEmpty()) {
            query.like(CtAction::getTargetKey, targetKey.trim());
        }
        query.orderByDesc(CtAction::getCreatedAt);
        return actionMapper.selectPage(new Page<>(current, size), query);
    }

    public List<Map<String, Object>> types() {
        return Arrays.asList(
                type("OMS_REROUTE_WAREHOUSE", "OMS", field("orderNo", "订单号", true),
                        field("warehouseCode", "仓库编码", true)),
                type("OMS_HOLD", "OMS", field("orderNo", "订单号", true)),
                type("OMS_UNHOLD", "OMS", field("orderNo", "订单号", true)),
                type("OMS_PRIORITIZE", "OMS", field("orderNo", "订单号", true),
                        field("priority", "优先级", true)),
                type("OMS_AUTO_PROCESS", "OMS", field("orderNo", "订单号", true)),
                type("OMS_CANCEL", "OMS", field("orderNo", "订单号", true)),
                type("WMS_ALLOCATE", "WMS", field("orderCode", "出库单号", true)),
                type("WMS_REPLENISH", "WMS", field("warehouseCode", "仓库编码", true)),
                type("TMS_DISPATCH", "TMS", field("waybillId", "运单号", true)),
                type("TMS_SYNC_TRACK", "TMS", field("waybillId", "运单号", true)),
                type("TMS_SWITCH_CARRIER", "TMS", field("waybillId", "运单号", true),
                        field("carrierCode", "承运商编码", true)),
                type("SRM_PURCHASE_SUGGEST", "SRM", field("sku", "SKU", true),
                        field("qty", "建议数量", true)));
    }

    private void mutateSnapshot(CtAction action, Map<String, Object> params) {
        if ("OMS_REROUTE_WAREHOUSE".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null && params.get("warehouseCode") != null) {
                order.setWarehouseCode(String.valueOf(params.get("warehouseCode")));
                orderMapper.updateById(order);
            }
        } else if ("OMS_CANCEL".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null) {
                order.setStatus("CANCELLED");
                orderMapper.updateById(order);
            }
        } else if ("WMS_ALLOCATE".equals(action.getType())) {
            WmsOrderSnapshot order = wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                    .eq(WmsOrderSnapshot::getCode, action.getTargetKey()));
            if (order != null) {
                order.setStatus("ALLOCATED");
                wmsMapper.updateById(order);
            }
        } else if ("TMS_SWITCH_CARRIER".equals(action.getType())) {
            ShipmentSnapshot shipment = shipmentMapper.selectOne(
                    new LambdaQueryWrapper<ShipmentSnapshot>()
                            .eq(ShipmentSnapshot::getWaybillCode, action.getTargetKey()));
            if (shipment != null && params.get("carrierCode") != null) {
                shipment.setCarrierCode(String.valueOf(params.get("carrierCode")));
                shipmentMapper.updateById(shipment);
            }
        }
    }

    private Map<String, Object> type(
            String name,
            String system,
            Map<String, Object>... params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", name);
        result.put("targetSystem", system);
        result.put("params", Arrays.asList(params));
        return result;
    }

    private Map<String, Object> field(
            String name,
            String label,
            boolean required) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("label", label);
        result.put("required", required);
        return result;
    }

    private String systemFor(String type) {
        if (type.startsWith("OMS_")) {
            return "OMS";
        }
        if (type.startsWith("WMS_")) {
            return "WMS";
        }
        if (type.startsWith("TMS_")) {
            return "TMS";
        }
        return "SRM";
    }

    private String operator() {
        User user = CurrentUser.get();
        return user == null ? "system" : user.getUsername();
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("动作参数格式错误", ex);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }
}
