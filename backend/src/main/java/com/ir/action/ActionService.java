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
import com.ir.snapshot.ExtSnapshot;
import com.ir.snapshot.ExtSnapshotMapper;
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
    private final ExtSnapshotMapper extMapper;
    private final ActionEnricher enricher;
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
            ActionEnricher enricher,
            ClientFactory clients,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.actionMapper = actionMapper;
        this.systemMapper = systemMapper;
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.extMapper = extMapper;
        this.enricher = enricher;
        this.clients = clients;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CtAction createAndExecute(Map<String, Object> request) {
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
        return create(request);
    }

    private CtAction create(Map<String, Object> request) {
        request = enricher.enrich(request);
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
                clients.tms(system).execute(command);
            } else {
                clients.ecosystem(system).execute(command);
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
                type("OMS_REROUTE_WAREHOUSE", "OMS", "OMS改仓", field("orderNo", "订单号", true),
                        field("warehouseCode", "仓库编码", true)),
                type("OMS_HOLD", "OMS", "OMS挂起", field("orderNo", "订单号", true)),
                type("OMS_UNHOLD", "OMS", "OMS解挂", field("orderNo", "订单号", true)),
                type("OMS_PRIORITIZE", "OMS", "OMS加急", field("orderNo", "订单号", true),
                        field("priority", "优先级", true)),
                type("OMS_AUTO_PROCESS", "OMS", "OMS自动处理", field("orderNo", "订单号", true)),
                type("OMS_CANCEL", "OMS", "OMS取消", field("orderNo", "订单号", true)),
                type("WMS_ALLOCATE", "WMS", "WMS分配", field("orderCode", "出库单号", true)),
                type("WMS_REPLENISH", "WMS", "WMS仓内补货", field("warehouseCode", "仓库编码", true)),
                type("TMS_DISPATCH", "TMS", "TMS调度", field("waybillId", "运单号", true)),
                type("TMS_SYNC_TRACK", "TMS", "TMS同步轨迹", field("waybillId", "运单号", true)),
                type("TMS_SWITCH_CARRIER", "TMS", "TMS换承运商", field("waybillId", "运单号", true),
                        field("carrierCode", "承运商编码", true)),
                type("SRM_PURCHASE_SUGGEST", "SRM", "SRM采购建议", field("sku", "SKU", true),
                        field("qty", "建议数量", true)),
                type("SRM_SUBMIT_PR", "SRM", "SRM提交采购申请", field("code", "采购申请号", true)),
                type("SRM_APPROVE_PR", "SRM", "SRM审批采购申请", field("code", "采购申请号", true)),
                type("SAP_CREATE_PR", "SAP", "SAP创建采购申请", field("sku", "物料号", true),
                        field("qty", "数量", true)),
                type("SAP_RELEASE_PR", "SAP", "SAP释放采购申请", field("banfn", "采购申请号", true)),
                type("SAP_RELEASE_MO", "SAP", "SAP释放生产订单", field("aufnr", "生产订单号", true)),
                type("BOM_EXPLODE", "BOM", "BOM展开", field("bomNo", "BOM 编号", true)),
                type("INV_SUBMIT_REQUEST", "INV", "INV提交开票", field("requestNo", "开票申请号", true)),
                type("INV_APPROVE_REQUEST", "INV", "INV审核开票", field("requestNo", "开票申请号", true)),
                type("CRM_ADVANCE_STAGE", "CRM", "CRM推进商机", field("opportunityId", "商机ID", true)),
                type("CRM_ESCALATE_CASE", "CRM", "CRM升级工单", field("caseNo", "工单号", true)),
                type("DMS_REPLENISH_SHORTAGE", "DMS", "DMS缺货补货", field("dealerCode", "经销商编码", true)),
                type("OA_START_WORKFLOW", "OA", "OA发起审批", field("targetKey", "业务单号", true),
                        field("definitionCode", "流程编码", false)));
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
        } else if (action.getTargetSystem() != null
                && ClientFactory.ecosystemCode(action.getTargetSystem())) {
            mutateExt(action, params);
        }
    }

    private void mutateExt(CtAction action, Map<String, Object> params) {
        ExtSnapshot snapshot = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, action.getTargetSystem())
                .eq(ExtSnapshot::getBizKey, action.getTargetKey())
                .last("LIMIT 1"));
        if (snapshot == null && params.get("sku") != null) {
            snapshot = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getSourceSystem, action.getTargetSystem())
                    .eq(ExtSnapshot::getSku, String.valueOf(params.get("sku")))
                    .last("LIMIT 1"));
        }
        if ("SAP_CREATE_PR".equals(action.getType())
                || "SRM_PURCHASE_SUGGEST".equals(action.getType())
                || "OA_START_WORKFLOW".equals(action.getType())
                || "DMS_REPLENISH_SHORTAGE".equals(action.getType())) {
            ExtSnapshot created = new ExtSnapshot();
            created.setSourceSystem(action.getTargetSystem());
            created.setDataType(createdType(action.getType()));
            created.setBizKey(action.getActionNo());
            created.setStatus(createdStatus(action.getType()));
            created.setSku(params.get("sku") == null ? action.getTargetKey()
                    : String.valueOf(params.get("sku")));
            created.setQty(decimal(params.get("qty") != null ? params.get("qty") : params.get("suggestQty")));
            created.setPlantCode(params.get("plantCode") == null ? null
                    : String.valueOf(params.get("plantCode")));
            created.setTitle(action.getType() + " " + action.getTargetKey());
            created.setSyncedAt(LocalDateTime.now());
            extMapper.insert(created);
            return;
        }
        if (snapshot == null) {
            return;
        }
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

    private static String createdType(String type) {
        if ("SAP_CREATE_PR".equals(type) || "SRM_PURCHASE_SUGGEST".equals(type)) {
            return "PR";
        }
        if ("OA_START_WORKFLOW".equals(type)) {
            return "WF_INSTANCE";
        }
        if ("DMS_REPLENISH_SHORTAGE".equals(type)) {
            return "REPLENISH";
        }
        return "ACTION";
    }

    private static String createdStatus(String type) {
        if ("SRM_PURCHASE_SUGGEST".equals(type)) {
            return "DRAFT";
        }
        if ("SAP_CREATE_PR".equals(type)) {
            return "CREATED";
        }
        if ("OA_START_WORKFLOW".equals(type)) {
            return "RUNNING";
        }
        return "DRAFT";
    }

    private Map<String, Object> type(
            String name,
            String system,
            String label,
            Map<String, Object>... params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", name);
        result.put("targetSystem", system);
        result.put("label", label);
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
}
