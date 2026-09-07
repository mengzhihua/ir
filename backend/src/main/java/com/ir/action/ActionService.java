package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
            execute(action, read(action.getParams()));
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
        request.put("params", read(original.getParams()));
        request.put("alertId", original.getAlertId());
        return createAndExecute(request);
    }

    public List<CtAction> page() {
        return actionMapper.selectList(
                new LambdaQueryWrapper<CtAction>().orderByDesc(CtAction::getCreatedAt));
    }

    public List<Map<String, Object>> types() {
        return Arrays.asList(
                type("OMS_REROUTE_WAREHOUSE", "OMS", "orderNo,warehouseCode"),
                type("OMS_HOLD", "OMS", "orderNo"),
                type("OMS_UNHOLD", "OMS", "orderNo"),
                type("OMS_PRIORITIZE", "OMS", "orderNo,priority"),
                type("OMS_AUTO_PROCESS", "OMS", "orderNo"),
                type("OMS_CANCEL", "OMS", "orderNo"),
                type("WMS_ALLOCATE", "WMS", "orderCode"),
                type("WMS_REPLENISH", "WMS", "warehouseCode"),
                type("TMS_DISPATCH", "TMS", "waybillId"),
                type("TMS_SYNC_TRACK", "TMS", "waybillId"),
                type("TMS_SWITCH_CARRIER", "TMS", "waybillId,carrierCode"),
                type("SRM_PURCHASE_SUGGEST", "SRM", "sku,qty"));
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

    private Map<String, Object> type(String name, String system, String params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", name);
        result.put("targetSystem", system);
        result.put("params", params);
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
