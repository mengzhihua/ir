package com.ir.action.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.common.CarrierCodes;
import com.ir.common.CodeGenerator;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.sandbox.service.BalanceAdvisor;
import com.ir.snapshot.entity.ExtSnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.ExtSnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
import com.ir.system.auth.CurrentUser;
import com.ir.system.entity.User;
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
    private final ExtSnapshotMapper extMapper;
    private final ClientFactory clients;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public ActionService(
            CtActionMapper actionMapper,
            CtSystemMapper systemMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            ExtSnapshotMapper extMapper,
            ClientFactory clients,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.systemMapper = systemMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.extMapper = extMapper;
        this.clients = clients;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CtAction createAndExecute(Map<String, Object> request) {
        String type = String.valueOf(request.get("type"));
        String targetKey = String.valueOf(request.get("targetKey"));
        supersedeRelatedPending(type, targetKey, null);
        CtAction existing = findPending(type, targetKey);
        Map<String, Object> params = paramsOf(request);
        if (existing != null) {
            refreshPending(existing, request, params);
            try {
                execute(existing, params);
            } catch (Exception ex) {
                existing.setStatus("FAILED");
                existing.setResult(ex.getMessage());
                existing.setExecutedAt(LocalDateTime.now());
                actionMapper.updateById(existing);
            }
            return actionMapper.selectById(existing.getId());
        }
        CtAction action = create(request);
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
        String type = String.valueOf(request.get("type"));
        String targetKey = String.valueOf(request.get("targetKey"));
        supersedeRelatedPending(type, targetKey, null);
        CtAction existing = findPending(type, targetKey);
        if (existing != null) {
            refreshPending(existing, request, paramsOf(request));
            actionMapper.updateById(existing);
            return existing;
        }
        return create(request);
    }

    @Transactional
    public CtAction executePending(Long id) {
        int claimed = actionMapper.update(null, new LambdaUpdateWrapper<CtAction>()
                .eq(CtAction::getId, id)
                .eq(CtAction::getStatus, "PENDING")
                .set(CtAction::getStatus, "RUNNING"));
        if (claimed == 0) {
            return actionMapper.selectById(id);
        }
        CtAction action = actionMapper.selectById(id);
        execute(action, read(action.getParamsJson()));
        return actionMapper.selectById(action.getId());
    }

    @Transactional
    public int supersedeOpposing(String stance) {
        int count = 0;
        for (CtAction action : actionMapper.selectList(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getStatus, "PENDING"))) {
            if (BalanceAdvisor.opposes(stance, action.getType(), carrierOf(action))) {
                markSuperseded(action, "立场改为 " + stance + "，作废冲突待办");
                count++;
            }
        }
        return count;
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
            } else if ("TMS".equals(action.getTargetSystem())) {
                if ("TMS_SWITCH_CARRIER".equals(action.getType())
                        && params.get("carrierCode") != null) {
                    params.put("carrierCode", CarrierCodes.toTms(
                            String.valueOf(params.get("carrierCode"))));
                    command.setParams(params);
                }
                clients.tms(system).execute(command);
            } else if ("SRM".equals(action.getTargetSystem())) {
                clients.srm(system).execute(command);
            } else if ("BMS".equals(action.getTargetSystem())) {
                throw new IllegalStateException("BMS 不接受控制塔指令");
            } else {
                clients.ecosystem(system).execute(command);
            }
            mutateSnapshot(action, params);
            action.setParams(write(params));
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
        request.put("expectedSaving", original.getExpectedSaving());
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
                        field("qty", "建议数量", true)),
                type("SRM_SUBMIT_PR", "SRM", field("code", "采购申请号", true)),
                type("SRM_APPROVE_PR", "SRM", field("code", "采购申请号", true)),
                type("SAP_CREATE_PR", "SAP", field("sku", "物料号", true),
                        field("qty", "数量", true)),
                type("SAP_RELEASE_PR", "SAP", field("banfn", "采购申请号", true)),
                type("SAP_RELEASE_MO", "SAP", field("aufnr", "生产订单号", true)),
                type("BOM_EXPLODE", "BOM", field("bomNo", "BOM 编号", true)),
                type("INV_SUBMIT_REQUEST", "INV", field("requestNo", "开票申请号", true)),
                type("INV_APPROVE_REQUEST", "INV", field("requestNo", "开票申请号", true)),
                type("CRM_ADVANCE_STAGE", "CRM", field("opportunityId", "商机ID", true)),
                type("CRM_ESCALATE_CASE", "CRM", field("caseNo", "工单号", true)),
                type("DMS_REPLENISH_SHORTAGE", "DMS", field("dealerCode", "经销商编码", true)),
                type("OA_START_WORKFLOW", "OA", field("targetKey", "业务单号", true),
                        field("definitionCode", "流程编码", false)),
                type("OA_APPROVE_TASK", "OA", field("taskId", "待办ID", true)));
    }

    private void mutateSnapshot(CtAction action, Map<String, Object> params) {
        if ("OMS_REROUTE_WAREHOUSE".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null && params.get("warehouseCode") != null) {
                order.setWarehouseCode(String.valueOf(params.get("warehouseCode")));
                orderMapper.updateById(order);
            }
        } else if ("OMS_HOLD".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null) {
                order.setStatus("HOLD");
                orderMapper.updateById(order);
            }
        } else if ("OMS_AUTO_PROCESS".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null) {
                order.setStatus("ALLOCATED");
                orderMapper.updateById(order);
            }
        } else if ("OMS_UNHOLD".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null) {
                order.setStatus("CREATED");
                orderMapper.updateById(order);
            }
        } else if ("OMS_PRIORITIZE".equals(action.getType())) {
            OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                    .eq(OrderSnapshot::getOrderNo, action.getTargetKey()));
            if (order != null) {
                order.setPriority(priorityOf(params));
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
            if (order == null) {
                order = wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                        .eq(WmsOrderSnapshot::getExternalNo, action.getTargetKey())
                        .last("LIMIT 1"));
            }
            if (order != null) {
                order.setStatus("ALLOCATED");
                wmsMapper.updateById(order);
            }
        } else if ("TMS_SWITCH_CARRIER".equals(action.getType())) {
            ShipmentSnapshot shipment = shipmentMapper.selectOne(
                    new LambdaQueryWrapper<ShipmentSnapshot>()
                            .eq(ShipmentSnapshot::getWaybillCode, action.getTargetKey()));
            if (shipment != null && params.get("carrierCode") != null) {
                String toCarrier = String.valueOf(params.get("carrierCode"));
                String fromCarrier = shipment.getCarrierCode();
                BigDecimal fromFreight = shipment.getFreightAmount();
                BigDecimal toFreight = CarrierCodes.scaledFreight(
                        fromCarrier, toCarrier, fromFreight);
                shipment.setCarrierCode(toCarrier);
                if (toFreight != null) {
                    shipment.setFreightAmount(toFreight);
                }
                shipmentMapper.updateById(shipment);
                if (fromCarrier != null) {
                    params.put("fromCarrierCode", fromCarrier);
                }
                if (fromFreight != null) {
                    params.put("fromFreightAmount", fromFreight);
                }
                if (fromFreight != null && toFreight != null) {
                    params.put("actualSaving", fromFreight.subtract(toFreight));
                }
            }
        } else if ("TMS_SYNC_TRACK".equals(action.getType())
                || "TMS_DISPATCH".equals(action.getType())) {
            ShipmentSnapshot shipment = shipmentOf(action.getTargetKey());
            if (shipment != null) {
                applyTmsTrack(action.getType(), shipment, params);
            }
        } else if ("SRM_PURCHASE_SUGGEST".equals(action.getType())
                || "SAP_CREATE_PR".equals(action.getType())) {
            writePurchaseInbound(action, params);
        } else if (action.getTargetSystem() != null
                && ClientFactory.ecosystemCode(action.getTargetSystem())) {
            ExtSnapshot snapshot = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getSourceSystem, action.getTargetSystem())
                    .eq(ExtSnapshot::getBizKey, action.getTargetKey())
                    .last("LIMIT 1"));
            if (snapshot != null) {
                if (action.getType().contains("SUBMIT")) {
                    snapshot.setStatus("SUBMITTED");
                } else if (action.getType().contains("APPROVE") || action.getType().contains("RELEASE")) {
                    snapshot.setStatus("RELEASED");
                } else if (action.getType().contains("ESCALATE")) {
                    snapshot.setStatus("ESCALATED");
                } else if (action.getType().contains("ADVANCE")) {
                    snapshot.setStatus("NEEDS_ANALYSIS");
                }
                extMapper.updateById(snapshot);
            }
        }
    }

    private Map<String, Object> paramsOf(Map<String, Object> request) {
        return request.get("params") instanceof Map
                ? objectMapper.convertValue(request.get("params"),
                new TypeReference<Map<String, Object>>() {
                })
                : new LinkedHashMap<>();
    }

    private void refreshPending(
            CtAction existing,
            Map<String, Object> request,
            Map<String, Object> params) {
        existing.setParams(write(params));
        existing.setExpectedSaving(decimal(request.get("expectedSaving")));
        if (request.get("alertId") != null) {
            existing.setAlertId(Long.valueOf(String.valueOf(request.get("alertId"))));
        }
    }

    private CtAction findPending(String type, String targetKey) {
        return actionMapper.selectOne(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getStatus, "PENDING")
                .eq(CtAction::getType, type)
                .eq(CtAction::getTargetKey, targetKey)
                .orderByDesc(CtAction::getId)
                .last("LIMIT 1"));
    }

    private void supersedeRelatedPending(String type, String targetKey, Long keepId) {
        if (targetKey == null) {
            return;
        }
        for (CtAction row : actionMapper.selectList(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getStatus, "PENDING")
                .eq(CtAction::getTargetKey, targetKey))) {
            if (keepId != null && keepId.equals(row.getId())) {
                continue;
            }
            if (type.equals(row.getType())) {
                continue;
            }
            if (omsStance(type) && omsStance(row.getType())) {
                markSuperseded(row, "同订单已改为 " + type);
            }
        }
    }

    private boolean omsStance(String type) {
        return "OMS_HOLD".equals(type)
                || "OMS_PRIORITIZE".equals(type)
                || "OMS_AUTO_PROCESS".equals(type);
    }

    private void markSuperseded(CtAction action, String result) {
        action.setStatus("SUPERSEDED");
        action.setResult(result);
        action.setExecutedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    private String carrierOf(CtAction action) {
        Map<String, Object> params = read(action.getParamsJson());
        Object value = params.get("carrierCode");
        return value == null ? null : String.valueOf(value);
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

    private int priorityOf(Map<String, Object> params) {
        Object value = params == null ? null : params.get("priority");
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return 10;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 10;
        }
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
        if (type.startsWith("SAP_")) {
            return "SAP";
        }
        if (type.startsWith("BOM_")) {
            return "BOM";
        }
        if (type.startsWith("INV_")) {
            return "INV";
        }
        if (type.startsWith("CRM_")) {
            return "CRM";
        }
        if (type.startsWith("DMS_")) {
            return "DMS";
        }
        if (type.startsWith("OA_")) {
            return "OA";
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

    private ShipmentSnapshot shipmentOf(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        ShipmentSnapshot shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<ShipmentSnapshot>()
                        .eq(ShipmentSnapshot::getWaybillCode, key)
                        .last("LIMIT 1"));
        if (shipment != null) {
            return shipment;
        }
        return shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentSnapshot>()
                .eq(ShipmentSnapshot::getSourceNo, key)
                .last("LIMIT 1"));
    }

    private void applyTmsTrack(String type, ShipmentSnapshot shipment, Map<String, Object> params) {
        if (terminalStatus(shipment.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String fromStatus = shipment.getStatus();
        Boolean fromException = shipment.getExceptionFlag();
        LocalDateTime fromEta = shipment.getPlannedArriveTime();
        if ("TMS_DISPATCH".equals(type)) {
            if (fromStatus == null || "CREATED".equals(fromStatus)) {
                shipment.setStatus("DISPATCHED");
            }
        } else {
            shipment.setStatus("IN_TRANSIT");
            shipment.setExceptionFlag(false);
            if (fromEta == null || !fromEta.isAfter(now)) {
                shipment.setPlannedArriveTime(now.plusHours(6));
            }
            shipment.setSyncedAt(now);
        }
        shipmentMapper.updateById(shipment);
        if (fromStatus != null) {
            params.put("fromStatus", fromStatus);
        }
        if (fromException != null) {
            params.put("fromExceptionFlag", fromException);
        }
        if (fromEta != null) {
            params.put("fromPlannedArriveTime", fromEta.toString());
        }
        if (shipment.getPlannedArriveTime() != null) {
            params.put("plannedArriveTime", shipment.getPlannedArriveTime().toString());
        }
    }

    private boolean terminalStatus(String status) {
        return "DELIVERED".equals(status)
                || "CLOSED".equals(status)
                || "CANCELLED".equals(status);
    }

    private void writePurchaseInbound(CtAction action, Map<String, Object> params) {
        String sku = firstText(params.get("sku"), params.get("matnr"), action.getTargetKey());
        if (sku != null && sku.contains("/")) {
            sku = sku.split("/")[0];
        }
        if (sku == null || sku.trim().isEmpty()) {
            return;
        }
        sku = sku.trim();
        BigDecimal qty = qtyOf(params.get("qty"), params.get("suggestQty"));
        String system = "SAP_CREATE_PR".equals(action.getType()) ? "SAP" : "SRM";
        String bizKey = "IR-PO-" + system + "-" + sku;
        ExtSnapshot existing = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, system)
                .eq(ExtSnapshot::getDataType, "PO")
                .eq(ExtSnapshot::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new ExtSnapshot();
            existing.setSourceSystem(system);
            existing.setDataType("PO");
            existing.setBizKey(bizKey);
            existing.setStatus("OPEN");
            existing.setSku(sku);
            existing.setQty(qty);
            existing.setPlantCode(plantOf(system, params));
            existing.setTitle("IR 采购在途 " + sku);
            existing.setSyncedAt(LocalDateTime.now());
            extMapper.insert(existing);
        } else {
            BigDecimal current = existing.getQty() == null ? BigDecimal.ZERO : existing.getQty();
            existing.setQty(current.add(qty));
            existing.setStatus("OPEN");
            existing.setSku(sku);
            existing.setSyncedAt(LocalDateTime.now());
            extMapper.updateById(existing);
        }
        params.put("poBizKey", bizKey);
        params.put("inTransitQty", existing.getQty());
    }

    private String plantOf(String system, Map<String, Object> params) {
        String plant = firstText(params.get("plantCode"), params.get("werks"));
        if (plant != null) {
            return plant;
        }
        return "SAP".equals(system) ? "1000" : "P001";
    }

    private BigDecimal qtyOf(Object... values) {
        for (Object value : values) {
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                continue;
            }
            try {
                BigDecimal qty = new BigDecimal(String.valueOf(value));
                if (qty.signum() > 0) {
                    return qty;
                }
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return BigDecimal.TEN;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()
                    && !"null".equals(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
