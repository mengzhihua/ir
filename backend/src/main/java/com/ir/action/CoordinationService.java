package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.snapshot.ExtSnapshot;
import com.ir.snapshot.ExtSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一条业务决策拆成可同时打到 SRM / SAP / OA / WMS / DMS / BOM 的指令集。
 * 沙盘落地、预测转指令、预警执行建议都走这里，避免只动 OTWB 或只动 SRM。
 */
@Service
public class CoordinationService {
    private static final Set<String> REPLENISH_TYPES = new LinkedHashSet<String>(Arrays.asList(
            "SRM_PURCHASE_SUGGEST", "SAP_CREATE_PR", "WMS_REPLENISH", "COORDINATE_REPLENISH"));

    private final ActionService actions;
    private final ActionEnricher enricher;
    private final ExtSnapshotMapper extMapper;

    public CoordinationService(
            ActionService actions,
            ActionEnricher enricher,
            ExtSnapshotMapper extMapper) {
        this.actions = actions;
        this.enricher = enricher;
        this.extMapper = extMapper;
    }

    public List<CtAction> dispatch(Map<String, Object> request, boolean execute) {
        List<CtAction> result = new ArrayList<>();
        for (Map<String, Object> plan : expand(request)) {
            result.add(execute ? actions.createAndExecute(plan) : actions.createPending(plan));
        }
        return result;
    }

    public List<CtAction> replenish(
            String sku,
            BigDecimal qty,
            String warehouseCode,
            BigDecimal expectedSaving,
            boolean execute,
            Long alertId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "SRM_PURCHASE_SUGGEST");
        request.put("targetKey", sku);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sku", sku);
        params.put("qty", qty);
        params.put("suggestQty", qty);
        if (warehouseCode != null) {
            params.put("warehouseCode", warehouseCode);
        }
        request.put("params", params);
        request.put("expectedSaving", expectedSaving);
        if (alertId != null) {
            request.put("alertId", alertId);
        }
        return dispatch(request, execute);
    }

    public List<Map<String, Object>> expand(Map<String, Object> request) {
        Map<String, Object> primary = enricher.enrich(copy(request));
        String type = str(primary.get("type"));
        if ("COORDINATE_REPLENISH".equals(type)) {
            primary.put("type", "SRM_PURCHASE_SUGGEST");
            type = "SRM_PURCHASE_SUGGEST";
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        add(plans, primary);
        Map<String, Object> params = paramsOf(primary);
        if (REPLENISH_TYPES.contains(type)) {
            add(plans, sibling("SRM_PURCHASE_SUGGEST", skuKey(primary), params, primary));
            add(plans, sibling("SAP_CREATE_PR", skuKey(primary), params, primary));
            add(plans, oaPlan(primary, "采购补货审批"));
            add(plans, wmsPlan(primary));
            addAll(plans, dmsPlans(primary));
            addAll(plans, bomPlans(primary));
        } else if ("SRM_SUBMIT_PR".equals(type) || "SRM_APPROVE_PR".equals(type)) {
            add(plans, oaPlan(primary, "采购申请审批"));
        } else if ("DMS_REPLENISH_SHORTAGE".equals(type)) {
            add(plans, sibling("SRM_PURCHASE_SUGGEST", skuKey(primary), params, primary));
            add(plans, sibling("SAP_CREATE_PR", skuKey(primary), params, primary));
            add(plans, oaPlan(primary, "经销商备件补货审批"));
        } else if ("CRM_ESCALATE_CASE".equals(type)) {
            add(plans, oaPlan(primary, "客服工单升级审批"));
        } else if ("INV_SUBMIT_REQUEST".equals(type)) {
            add(plans, oaPlan(primary, "开票申请审批"));
        }
        return plans;
    }

    private Map<String, Object> sibling(
            String type,
            String targetKey,
            Map<String, Object> params,
            Map<String, Object> seed) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("type", type);
        plan.put("targetKey", targetKey);
        plan.put("params", new LinkedHashMap<String, Object>(params));
        copyMeta(seed, plan);
        return enricher.enrich(plan);
    }

    private Map<String, Object> oaPlan(Map<String, Object> seed, String titlePrefix) {
        String targetKey = first(skuKey(seed), str(seed.get("targetKey")));
        Map<String, Object> params = paramsOf(seed);
        params.put("definitionCode", "GENERAL");
        params.put("businessType", "IR");
        params.put("businessId", targetKey);
        params.put("title", titlePrefix + " " + targetKey);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("type", "OA_START_WORKFLOW");
        plan.put("targetKey", targetKey);
        plan.put("params", params);
        copyMeta(seed, plan);
        return enricher.enrich(plan);
    }

    private Map<String, Object> wmsPlan(Map<String, Object> seed) {
        Map<String, Object> params = paramsOf(seed);
        String warehouse = first(str(params.get("warehouseCode")), "WH-SH");
        params.put("warehouseCode", warehouse);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("type", "WMS_REPLENISH");
        plan.put("targetKey", warehouse);
        plan.put("params", params);
        copyMeta(seed, plan);
        return enricher.enrich(plan);
    }

    private List<Map<String, Object>> dmsPlans(Map<String, Object> seed) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sku = skuKey(seed);
        if (sku == null) {
            return result;
        }
        for (ExtSnapshot row : extMapper.selectList(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, "DMS")
                .eq(ExtSnapshot::getDataType, "SHORTAGE")
                .eq(ExtSnapshot::getSku, sku))) {
            Map<String, Object> params = paramsOf(seed);
            params.put("dealerCode", row.getPlantCode());
            params.put("sku", sku);
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("type", "DMS_REPLENISH_SHORTAGE");
            plan.put("targetKey", row.getBizKey());
            plan.put("params", params);
            copyMeta(seed, plan);
            add(result, enricher.enrich(plan));
        }
        return result;
    }

    private List<Map<String, Object>> bomPlans(Map<String, Object> seed) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sku = skuKey(seed);
        if (sku == null) {
            return result;
        }
        ExtSnapshot bom = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                .eq(ExtSnapshot::getSourceSystem, "BOM")
                .eq(ExtSnapshot::getDataType, "BOM")
                .eq(ExtSnapshot::getSku, sku)
                .last("LIMIT 1"));
        if (bom == null) {
            return result;
        }
        Map<String, Object> params = paramsOf(seed);
        params.put("bomNo", bom.getBizKey());
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("type", "BOM_EXPLODE");
        plan.put("targetKey", bom.getBizKey());
        plan.put("params", params);
        copyMeta(seed, plan);
        add(result, enricher.enrich(plan));
        return result;
    }

    private static void add(List<Map<String, Object>> plans, Map<String, Object> plan) {
        if (plan == null || plan.get("type") == null) {
            return;
        }
        String type = str(plan.get("type"));
        String key = str(plan.get("targetKey"));
        for (Map<String, Object> existing : plans) {
            if (type.equals(str(existing.get("type"))) && String.valueOf(key)
                    .equals(String.valueOf(existing.get("targetKey")))) {
                return;
            }
        }
        plans.add(plan);
    }

    private static void addAll(List<Map<String, Object>> plans, List<Map<String, Object>> extra) {
        for (Map<String, Object> plan : extra) {
            add(plans, plan);
        }
    }

    private static void copyMeta(Map<String, Object> seed, Map<String, Object> plan) {
        if (seed.get("expectedSaving") != null) {
            plan.put("expectedSaving", seed.get("expectedSaving"));
        }
        if (seed.get("alertId") != null) {
            plan.put("alertId", seed.get("alertId"));
        }
    }

    private static String skuKey(Map<String, Object> plan) {
        Map<String, Object> params = paramsOf(plan);
        return first(str(params.get("sku")), str(params.get("matnr")), str(plan.get("targetKey")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsOf(Map<String, Object> request) {
        Object value = request.get("params");
        return value instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) value)
                : new LinkedHashMap<String, Object>();
    }

    private static Map<String, Object> copy(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(request);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
