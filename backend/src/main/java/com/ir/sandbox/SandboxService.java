package com.ir.sandbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.SalesDaily;
import com.ir.snapshot.SalesDailyMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SandboxService {
    private final CtScenarioMapper scenarioMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final SandboxEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.scenarioMapper = scenarioMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
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

    public List<CtScenario> page() {
        return scenarioMapper.selectList(
                new LambdaQueryWrapper<CtScenario>()
                        .orderByDesc(CtScenario::getCreatedAt));
    }

    public List<Map<String, Object>> compare(String ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        CtScenario baseline = baseline();
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
        List<CtAction> result = new ArrayList<>();
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())
                || "LOWEST_COST".equals(params.getAllocationStrategy())) {
            for (String sku : Arrays.asList(
                    "SKU001", "SKU002", "SKU003", "SKU004", "SKU005")) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("type", "SRM_PURCHASE_SUGGEST");
                request.put("targetKey", sku);
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("sku", sku);
                actionParams.put("qty", 0);
                request.put("params", actionParams);
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
        }
        return data;
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
}
