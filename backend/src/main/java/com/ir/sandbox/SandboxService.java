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
    private final SandboxEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            OrderSnapshotMapper orderMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.scenarioMapper = scenarioMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.orderMapper = orderMapper;
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
        return saveScenario(name, params, false, false);
    }

    public CtScenario run(Long id) {
        CtScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            return null;
        }
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class);
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
                    .divide(baseCost, 6, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
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
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class);
        ScenarioParams baseParams = read(baseline().getParamsJson(),
                ScenarioParams.class);
        BaselineData baselineData = baselineData();
        List<CtAction> result = new ArrayList<>();
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())
                || "LOWEST_COST".equals(params.getAllocationStrategy())) {
            String targetWarehouse = params.getSingleWarehouse() == null
                    ? "WH-SH" : params.getSingleWarehouse();
            for (String sku : baselineData.getDemandBySku().keySet()) {
                String current = baselineData.getSkuWarehouse().get(sku);
                if (targetWarehouse.equals(current)) {
                    continue;
                }
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "OMS_REROUTE_WAREHOUSE");
                request.put("targetKey", sku);
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("sku", sku);
                actionParams.put("warehouseCode", targetWarehouse);
                request.put("params", actionParams);
                result.add(actions.createPending(request));
            }
        }
        if (!params.getCarrierMix().equals(baseParams.getCarrierMix())) {
            for (String carrier : params.getCarrierMix().keySet()) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "TMS_SWITCH_CARRIER");
                request.put("targetKey", carrier);
                request.put("params", params.getCarrierMix());
                result.add(actions.createPending(request));
            }
        }
        Map<String, Object> scenarioResult = result(scenario);
        Object stockout = scenarioResult.get("stockoutUnits");
        if (stockout != null && new BigDecimal(String.valueOf(stockout))
                .signum() > 0) {
            for (String sku : baselineData.getDemandBySku().keySet()) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "SRM_PURCHASE_SUGGEST");
                request.put("targetKey", sku);
                request.put("params", Collections.singletonMap("sku", sku));
                result.add(actions.createPending(request));
            }
        }
        return result;
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
        return data;
    }

    private void addShare(
            Map<String, Map<String, BigDecimal>> shares,
            String key,
            String dimension,
            BigDecimal amount) {
        Map<String, BigDecimal> values = shares.computeIfAbsent(
                key, ignored -> new LinkedHashMap<>());
        values.put(dimension, values.getOrDefault(dimension,
                BigDecimal.ZERO).add(amount));
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values.values()) {
            total = total.add(value);
        }
        for (String name : new ArrayList<>(values.keySet())) {
            values.put(name, values.get(name).divide(total, 6,
                    BigDecimal.ROUND_HALF_UP));
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
