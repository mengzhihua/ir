package com.ir.sandbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.SalesDaily;
import com.ir.snapshot.SalesDailyMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SandboxService {
    private final CtScenarioMapper scenarioMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final OrderSnapshotMapper orderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final SandboxEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            OrderSnapshotMapper orderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.scenarioMapper = scenarioMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.engine = engine;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    public synchronized CtScenario baseline() {
        CtScenario existing = scenarioMapper.selectOne(
                new LambdaQueryWrapper<CtScenario>()
                        .eq(CtScenario::getBaseline, true)
                        .orderByAsc(CtScenario::getId)
                        .last("LIMIT 1"));
        return existing == null
                ? createAndRun("基线场景", new ScenarioParams(), true)
                : existing;
    }

    public void ensureBaseline() {
        baseline();
    }

    public CtScenario create(String name, ScenarioParams params) {
        return saveScenario(name,
                params == null ? new ScenarioParams() : params.normalized(),
                false, false);
    }

    public CtScenario run(Long id) {
        CtScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            return null;
        }
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class);
        params = params.normalized();
        return saveScenario(scenario.getName(), params,
                Boolean.TRUE.equals(scenario.getBaseline()), true, scenario);
    }

    public CtScenario get(Long id) {
        return scenarioMapper.selectById(id);
    }

    public Page<CtScenario> page(
            String name,
            String status,
            long current,
            long size) {
        LambdaQueryWrapper<CtScenario> query = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            query.like(CtScenario::getName, name.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            query.eq(CtScenario::getStatus, status);
        }
        query.orderByDesc(CtScenario::getCreatedAt);
        return scenarioMapper.selectPage(new Page<>(current, size), query);
    }

    public List<Map<String, Object>> compare(String ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        CtScenario baseline = baseline();
        Map<String, Object> baseResult = result(baseline);
        for (String value : ids.split(",")) {
            CtScenario scenario = get(Long.valueOf(value.trim()));
            if (scenario == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", scenario);
            BigDecimal baseCost = baseline.getTotalCost() == null
                    ? BigDecimal.ZERO : baseline.getTotalCost();
            BigDecimal cost = scenario.getTotalCost() == null
                    ? BigDecimal.ZERO : scenario.getTotalCost();
            BigDecimal saving = baseCost.signum() == 0
                    ? BigDecimal.ZERO
                    : baseCost.subtract(cost)
                    .divide(baseCost, 6, BigDecimal.ROUND_HALF_UP);
            row.put("savingPercent", saving);
            row.put("savingPct", saving);
            row.put("delta", deltas(baseResult, result(scenario)));
            result.add(row);
        }
        return result;
    }

    public List<CtAction> apply(Long id) {
        CtScenario scenario = get(id);
        if (scenario == null) {
            return new ArrayList<>();
        }
        ScenarioParams params = read(
                scenario.getParamsJson(), ScenarioParams.class).normalized();
        BaselineData baselineData = baselineData();
        List<CtAction> result = new ArrayList<>();
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())
                || "LOWEST_COST".equals(params.getAllocationStrategy())) {
            for (OrderSnapshot order : orderMapper.selectList(
                    new LambdaQueryWrapper<OrderSnapshot>()
                            .in(OrderSnapshot::getStatus,
                                    "CREATED", "AUDITED", "ALLOCATED"))) {
                String targetWarehouse = params.getSingleWarehouse() == null
                        ? "WH-SH" : params.getSingleWarehouse();
                if (targetWarehouse.equals(order.getWarehouseCode())) {
                    continue;
                }
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "OMS_REROUTE_WAREHOUSE");
                request.put("targetKey", order.getOrderNo());
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("warehouseCode", targetWarehouse);
                request.put("params", actionParams);
                result.add(actions.createPending(request));
                if (result.size() >= 50) {
                    return result;
                }
            }
        }
        String preferredCarrier = highestShare(params.getCarrierMix());
        if (preferredCarrier != null) {
            for (ShipmentSnapshot shipment : shipmentMapper.selectList(
                    new LambdaQueryWrapper<ShipmentSnapshot>()
                            .eq(ShipmentSnapshot::getStatus, "IN_TRANSIT"))) {
                BigDecimal share = params.getCarrierMix().getOrDefault(
                        shipment.getCarrierCode(), BigDecimal.ZERO);
                if (share.signum() != 0) {
                    continue;
                }
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "TMS_SWITCH_CARRIER");
                request.put("targetKey", shipment.getWaybillCode());
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("carrierCode", preferredCarrier);
                request.put("params", actionParams);
                result.add(actions.createPending(request));
                if (result.size() >= 50) {
                    return result;
                }
            }
        }
        Map<String, Object> scenarioResult = result(scenario);
        Object summaries = scenarioResult.get("perSkuSummary");
        if (summaries instanceof List) {
            for (Object value : (List<?>) summaries) {
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<?, ?> summary = (Map<?, ?>) value;
                BigDecimal stockout = decimal(summary.get("stockout"));
                if (stockout.signum() <= 0) {
                    continue;
                }
                String sku = String.valueOf(summary.get("sku"));
                String warehouse = params.getSingleWarehouse() == null
                        ? baselineData.getSkuWarehouse().get(sku)
                        : params.getSingleWarehouse();
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "WMS_REPLENISH");
                request.put("targetKey", warehouse + "/" + sku);
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("sku", sku);
                actionParams.put("warehouseCode", warehouse);
                actionParams.put("qty", stockout);
                request.put("params", actionParams);
                result.add(actions.createPending(request));
                if (result.size() >= 50) {
                    return result;
                }
            }
        }
        return result;
    }

    private String highestShare(Map<String, BigDecimal> shares) {
        String result = null;
        BigDecimal highest = null;
        for (Map.Entry<String, BigDecimal> entry : shares.entrySet()) {
            if (highest == null || entry.getValue().compareTo(highest) > 0) {
                result = entry.getKey();
                highest = entry.getValue();
            }
        }
        return result;
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO
                : new BigDecimal(String.valueOf(value));
    }

    private CtScenario createAndRun(
            String name,
            ScenarioParams params,
            boolean baseline) {
        return saveScenario(name, params, baseline, false);
    }

    private CtScenario saveScenario(
            String name,
            ScenarioParams params,
            boolean baseline,
            boolean update) {
        return saveScenario(name, params, baseline, update, null);
    }

    private CtScenario saveScenario(
            String name,
            ScenarioParams params,
            boolean baseline,
            boolean update,
            CtScenario existing) {
        params = params == null ? new ScenarioParams() : params.normalized();
        SandboxEngine.Result result = engine.run(params, baselineData());
        CtScenario scenario = existing == null ? new CtScenario() : existing;
        if (scenario.getScenarioNo() == null) {
            scenario.setScenarioNo(codes.next("SC"));
        }
        scenario.setName(name);
        scenario.setBaseline(baseline);
        scenario.setParamsJson(write(params));
        scenario.setResultJson(write(result));
        scenario.setStatus("RUN");
        scenario.setTotalCost(result.getTotalCost());
        scenario.setServiceLevel(result.getServiceLevel());
        if (update) {
            scenarioMapper.updateById(scenario);
        } else {
            scenarioMapper.insert(scenario);
        }
        return scenario;
    }

    private BaselineData baselineData() {
        BaselineData data = new BaselineData();
        data.setInventory(inventoryMapper.selectList(null));
        List<SalesDaily> sales = salesMapper.selectList(
                new LambdaQueryWrapper<SalesDaily>()
                        .orderByAsc(SalesDaily::getSalesDate));
        for (SalesDaily row : sales) {
            data.getDemandBySku()
                    .computeIfAbsent(row.getSku(), key -> new ArrayList<>())
                    .add(row.getQty());
            data.getSkuWarehouse().putIfAbsent(row.getSku(), row.getWarehouseCode());
            addShare(data.getChannelShare(), row.getSku(),
                    row.getChannelCode(), row.getQty());
        }
        for (OrderSnapshot row : orderMapper.selectList(null)) {
            addShare(data.getRegionShare(), "*",
                    region(row.getProvince()), BigDecimal.ONE);
        }
        for (String sku : data.getDemandBySku().keySet()) {
            if (!data.getRegionShare().containsKey(sku)) {
                data.getRegionShare().put(sku,
                        new LinkedHashMap<>(data.getRegionShare().get("*")));
            }
        }
        normalizeShares(data.getChannelShare());
        normalizeShares(data.getRegionShare());
        return data;
    }

    void addShare(
            Map<String, Map<String, BigDecimal>> shares,
            String key,
            String dimension,
            BigDecimal amount) {
        Map<String, BigDecimal> values = shares.computeIfAbsent(
                key, ignored -> new LinkedHashMap<>());
        values.put(dimension, values.getOrDefault(dimension,
                BigDecimal.ZERO).add(amount));
    }

    void normalizeShares(
            Map<String, Map<String, BigDecimal>> shares) {
        for (Map<String, BigDecimal> values : shares.values()) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values.values()) {
            total = total.add(value);
        }
            if (total.signum() == 0) {
                continue;
            }
        for (String name : new ArrayList<>(values.keySet())) {
            values.put(name, values.get(name).divide(total, 6,
                    BigDecimal.ROUND_HALF_UP));
        }
        }
    }

    private String region(String province) {
        if (province != null && (province.contains("北京")
                || province.contains("天津")
                || province.contains("河北")
                || province.contains("山东"))) {
            return "华北";
        }
        if (province != null && (province.contains("广东")
                || province.contains("广西")
                || province.contains("海南"))) {
            return "华南";
        }
        return "华东";
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("场景参数格式错误", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("场景结果保存失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(CtScenario scenario) {
        return read(scenario.getResultJson(), Map.class);
    }

    private Map<String, Object> deltas(
            Map<String, Object> baseline,
            Map<String, Object> scenario) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("totalCost", difference(scenario, baseline, "totalCost"));
        delta.put("serviceLevel", difference(scenario, baseline, "serviceLevel"));
        delta.put("stockoutUnits", difference(scenario, baseline,
                "stockoutUnits"));
        delta.put("avgLeadDays", difference(scenario, baseline,
                "avgLeadDays"));
        return delta;
    }

    private BigDecimal difference(
            Map<String, Object> left,
            Map<String, Object> right,
            String key) {
        BigDecimal leftValue = new BigDecimal(String.valueOf(
                left.getOrDefault(key, 0)));
        BigDecimal rightValue = new BigDecimal(String.valueOf(
                right.getOrDefault(key, 0)));
        return leftValue.subtract(rightValue);
    }
}
