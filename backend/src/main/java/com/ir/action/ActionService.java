package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.common.BizException;
import com.ir.common.CodeGenerator;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import com.ir.system.CurrentUser;
import com.ir.system.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActionService {
    static final String IDEMPOTENCY_KEY = "idempotencyKey";
    static final String RETRY_OF = "retryOf";
    private final CtActionMapper actionMapper;
    private final CtSystemMapper systemMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final ClientFactory clients;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public ActionService(
            CtActionMapper actionMapper,
            CtSystemMapper systemMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            PurchaseSnapshotMapper purchaseMapper,
            ClientFactory clients,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.systemMapper = systemMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.clients = clients;
        this.inventoryMapper = inventoryMapper;
        this.purchaseMapper = purchaseMapper;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CtAction createAndExecute(Map<String, Object> request) {
        return createAndExecute(request, null);
    }

    private CtAction createAndExecute(Map<String, Object> request, String idempotencyKey) {
        CtAction action = create(request, idempotencyKey);
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
        return create(request, null);
    }

    /** 幂等键只由服务端分配(新动作=动作号,重试=原动作键),忽略请求中携带的键. */
    private CtAction create(Map<String, Object> request, String idempotencyKey) {
        String type = String.valueOf(request.get("type"));
        String targetKey = String.valueOf(request.get("targetKey"));
        Map<String, Object> params = request.get("params") instanceof Map
                ? objectMapper.convertValue(request.get("params"),
                new TypeReference<Map<String, Object>>() {
                })
                : new LinkedHashMap<>();

        CtAction action = new CtAction();
        action.setActionNo(codes.next("ACT"));
        params.put(IDEMPOTENCY_KEY,
                idempotencyKey == null ? action.getActionNo() : idempotencyKey);
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
        InventorySnapshot reserved = null;
        boolean dispatched = false;
        try {
            CtSystem system = systemMapper.selectOne(
                    new LambdaQueryWrapper<CtSystem>()
                            .eq(CtSystem::getCode, action.getTargetSystem()));
            ActionCommand command = new ActionCommand();
            command.setType(action.getType());
            command.setTargetKey(action.getTargetKey());
            command.setParams(params);
            command.setIdempotencyKey(String.valueOf(params.get(IDEMPOTENCY_KEY)));
            if (system == null || !Boolean.TRUE.equals(system.getEnabled())) {
                throw new IllegalStateException(action.getTargetSystem() + " 未接入或已停用");
            }
            validate(action, params);
            reserved = reserveTransferSource(action, params);
            String result = "指令执行成功";
            if ("OMS".equals(action.getTargetSystem())) {
                clients.oms(system).execute(command);
            } else if ("WMS".equals(action.getTargetSystem())) {
                clients.wms(system).execute(command);
            } else if ("SRM".equals(action.getTargetSystem())) {
                Map<String, Object> reply = clients.srm(system).execute(command);
                if (reply != null && !reply.isEmpty()) {
                    result = write(reply);
                }
            } else {
                clients.tms(system).execute(command);
            }
            dispatched = true;
            try {
                mutateSnapshot(action, params);
                action.setResult(result);
            } catch (RuntimeException ex) {
                action.setResult(result + ";本地快照更新失败,待对账: " + ex.getMessage());
            }
            action.setStatus("SUCCESS");
        } catch (Exception ex) {
            boolean unknown = !dispatched && ex instanceof IntegrationException
                    && ((IntegrationException) ex).isOutcomeUnknown();
            if (unknown) {
                action.setStatus("UNKNOWN");
                action.setResult("远端结果未知,已保留预占,待对账(幂等键 "
                        + params.get(IDEMPOTENCY_KEY) + "): " + ex.getMessage());
            } else {
                if (reserved != null && !dispatched) {
                    adjustInventory(reserved.getId(), decimal(params.get("qty")));
                }
                action.setStatus("FAILED");
                action.setResult(ex.getMessage());
            }
        }
        action.setExecutedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    /** 重试:原动作 FAILED→RETRIED 原子抢占,一个幂等键的本地落账只允许发生一次. */
    @Transactional
    public CtAction retry(Long id) {
        CtAction original = actionMapper.selectById(id);
        if (original == null) {
            return null;
        }
        int claimed = actionMapper.update(null, new LambdaUpdateWrapper<CtAction>()
                .eq(CtAction::getId, id)
                .eq(CtAction::getStatus, "FAILED")
                .set(CtAction::getStatus, "RETRIED"));
        if (claimed == 0) {
            throw new BizException("仅 FAILED 状态的动作可重试,且每个动作只能重试一次(当前 "
                    + original.getStatus() + ")");
        }
        Map<String, Object> params = read(original.getParamsJson());
        Object key = params.get(IDEMPOTENCY_KEY);
        params.put(RETRY_OF, original.getActionNo());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", original.getType());
        request.put("targetKey", original.getTargetKey());
        request.put("params", params);
        request.put("alertId", original.getAlertId());
        return createAndExecute(request,
                key == null ? original.getActionNo() : String.valueOf(key));
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
                        field("qty", "建议数量", true), field("plantCode", "工厂", false),
                        field("reason", "原因", false)),
                type("SRM_EXPEDITE_PO", "SRM", field("poCode", "采购订单号", true),
                        field("reason", "原因", false)));
    }

    private void validate(CtAction action, Map<String, Object> params) {
        if ("WMS_REPLENISH".equals(action.getType()) && params.get("fromWarehouseCode") != null) {
            if (decimal(params.get("qty")).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("调拨缺少有效数量 qty");
            }
        } else if ("SRM_PURCHASE_SUGGEST".equals(action.getType())
                && decimal(params.get("qty")).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("采购建议缺少有效数量 qty");
        }
    }

    /**
     * 调拨前原子预占来源仓库存:条件更新 qty_available >= qty,更新行数不为 1 则库存不足,
     * 避免并发调拨重复使用同一份库存;外部调用失败时由调用方回补.
     */
    private InventorySnapshot reserveTransferSource(CtAction action, Map<String, Object> params) {
        if (!"WMS_REPLENISH".equals(action.getType()) || params.get("fromWarehouseCode") == null) {
            return null;
        }
        InventorySnapshot source = transferSource(params);
        BigDecimal qty = decimal(params.get("qty"));
        int updated = source == null ? 0 : inventoryMapper.update(null,
                new LambdaUpdateWrapper<InventorySnapshot>()
                        .eq(InventorySnapshot::getId, source.getId())
                        .ge(InventorySnapshot::getQtyAvailable, qty)
                        .setSql("qty_available = qty_available - " + qty.toPlainString())
                        .setSql("qty_on_hand = qty_on_hand - " + qty.toPlainString()));
        if (updated != 1) {
            throw new IllegalStateException("来源仓 " + params.get("fromWarehouseCode")
                    + " 可用库存不足,无法调拨 " + qty + " 件 " + params.get("sku"));
        }
        return source;
    }

    private void adjustInventory(Long id, BigDecimal delta) {
        inventoryMapper.update(null, new LambdaUpdateWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getId, id)
                .setSql("qty_available = qty_available + " + delta.toPlainString())
                .setSql("qty_on_hand = qty_on_hand + " + delta.toPlainString()));
    }

    private InventorySnapshot transferSource(Map<String, Object> params) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "WMS")
                .eq(InventorySnapshot::getWarehouseCode, String.valueOf(params.get("fromWarehouseCode")))
                .eq(InventorySnapshot::getSku, String.valueOf(params.get("sku"))));
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
        } else if ("WMS_REPLENISH".equals(action.getType())) {
            String sku = params.get("sku") == null ? null : String.valueOf(params.get("sku"));
            String warehouse = params.get("warehouseCode") == null
                    ? action.getTargetKey() : String.valueOf(params.get("warehouseCode"));
            if (sku != null && params.get("qty") != null) {
                BigDecimal qty = decimal(params.get("qty"));
                InventorySnapshot item = inventoryMapper.selectOne(new LambdaQueryWrapper<InventorySnapshot>()
                        .eq(InventorySnapshot::getSourceSystem, "WMS")
                        .eq(InventorySnapshot::getWarehouseCode, warehouse)
                        .eq(InventorySnapshot::getSku, sku));
                if (item != null) {
                    adjustInventory(item.getId(), qty);
                }
            }
        } else if ("SRM_EXPEDITE_PO".equals(action.getType())) {
            PurchaseSnapshot po = purchaseMapper.selectOne(new LambdaQueryWrapper<PurchaseSnapshot>()
                    .eq(PurchaseSnapshot::getDocType, "PO")
                    .eq(PurchaseSnapshot::getCode, action.getTargetKey()));
            if (po != null) {
                po.setExpectedDate(LocalDate.now().plusDays(1));
                purchaseMapper.updateById(po);
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
