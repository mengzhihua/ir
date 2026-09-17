package com.ir.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.common.CarrierCodes;
import com.ir.common.CodeGenerator;
import com.ir.sandbox.engine.BalanceScorer;
import com.ir.sandbox.engine.BaselineData;
import com.ir.sandbox.engine.SandboxEngine;
import com.ir.sandbox.engine.ScenarioParams;
import com.ir.sandbox.entity.CtScenario;
import com.ir.sandbox.mapper.CtScenarioMapper;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
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
                ? persist("基线场景", new ScenarioParams(), true, "BASELINE", null, false, null)
                : existing;
    }

    public void ensureBaseline() {
        baseline();
    }

    public CtScenario create(String name, ScenarioParams params) {
        return persist(name, params, false, "MANUAL", null, false, null);
    }

    public CtScenario persistAuto(String name, ScenarioParams params, String runNo) {
        return persist(name, params, false, "AUTO", runNo, false, null);
    }

    public CtScenario run(Long id) {
        CtScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            return null;
        }
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class);
        String kind = scenario.getKind() == null ? "MANUAL" : scenario.getKind();
        return persist(scenario.getName(), params,
                Boolean.TRUE.equals(scenario.getBaseline()),
                kind, scenario.getRunNo(), true, scenario);
    }

    public CtScenario get(Long id) {
        return scenarioMapper.selectById(id);
    }

    public Page<CtScenario> page(
            String name,
            String status,
            String kind,
            long current,
            long size) {
        LambdaQueryWrapper<CtScenario> query = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            query.like(CtScenario::getName, name.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            query.eq(CtScenario::getStatus, status);
        }
        if ("MANUAL".equalsIgnoreCase(kind)) {
            query.in(CtScenario::getKind, "MANUAL", "BASELINE");
        } else if (kind != null && !kind.trim().isEmpty()) {
            query.eq(CtScenario::getKind, kind.trim());
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
        return apply(id, false);
    }

    public List<CtAction> apply(Long id, boolean execute) {
        CtScenario scenario = get(id);
        if (scenario == null) {
            return new ArrayList<>();
        }
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class).normalized();
        ScenarioParams baseParams = read(baseline().getParamsJson(), ScenarioParams.class).normalized();
        List<CtAction> result = new ArrayList<>();
        List<Map<String, Object>> jobs = new ArrayList<>();
        BigDecimal expected = expectedSaving(baseline(), scenario);

        int reroutes = 0;
        String targetWarehouse = preferredWarehouse(params);
        if (targetWarehouse != null) {
            for (OrderSnapshot order : pendingOrders()) {
                if (reroutes >= 8) {
                    break;
                }
                if (targetWarehouse.equals(order.getWarehouseCode())) {
                    continue;
                }
                jobs.add(job("OMS_REROUTE_WAREHOUSE", order.getOrderNo(),
                        map("warehouseCode", targetWarehouse, "sku", firstSku()),
                        null));
                reroutes++;
            }
        }

        int switches = 0;
        String targetCarrier = dominantCarrier(params.getCarrierMix());
        String baseCarrier = dominantCarrier(baseParams.getCarrierMix());
        if (targetCarrier != null && !targetCarrier.equals(baseCarrier)) {
            for (ShipmentSnapshot shipment : openShipments()) {
                if (switches >= 8) {
                    break;
                }
                if (targetCarrier.equals(shipment.getCarrierCode())) {
                    continue;
                }
                String mapped = CarrierCodes.toTms(targetCarrier);
                jobs.add(job("TMS_SWITCH_CARRIER", shipment.getWaybillCode(),
                        map("carrierCode", mapped),
                        BalanceAdvisor.freightSaving(
                                shipment.getCarrierCode(), mapped, shipment.getFreightAmount())));
                switches++;
            }
        }

        Map<String, Object> scenarioResult = result(scenario);
        Object summaries = scenarioResult.get("perSkuSummary");
        int purchases = 0;
        if (summaries instanceof List) {
            for (Object row : (List<?>) summaries) {
                if (purchases >= 8) {
                    break;
                }
                if (!(row instanceof Map)) {
                    continue;
                }
                Map<?, ?> summary = (Map<?, ?>) row;
                BigDecimal stockout = decimal(summary.get("stockout"));
                if (stockout.signum() <= 0) {
                    continue;
                }
                String sku = String.valueOf(summary.get("sku"));
                jobs.add(job("SRM_PURCHASE_SUGGEST", sku,
                        map("sku", sku, "qty", stockout, "suggestQty", stockout),
                        null));
                purchases++;
            }
        }
        BigDecimal leftover = expected;
        int unassigned = 0;
        for (Map<String, Object> job : jobs) {
            if (job.get("expectedSaving") != null) {
                leftover = leftover.subtract((BigDecimal) job.get("expectedSaving")).max(BigDecimal.ZERO);
            } else {
                unassigned++;
            }
        }
        BigDecimal share = BalanceAdvisor.shareSaving(leftover, unassigned);
        for (Map<String, Object> job : jobs) {
            BigDecimal saving = (BigDecimal) job.get("expectedSaving");
            result.add(dispatch(
                    String.valueOf(job.get("type")),
                    String.valueOf(job.get("targetKey")),
                    paramsOf(job),
                    saving == null ? share : saving,
                    execute));
        }
        return result;
    }

    public BaselineData currentBaselineData() {
        return baselineData();
    }

    public void markRecommended(List<CtScenario> rows, Long recommendedId) {
        for (CtScenario row : rows) {
            row.setRecommended(row.getId() != null && row.getId().equals(recommendedId));
            scenarioMapper.updateById(row);
        }
    }

    public void rescore(List<CtScenario> rows, BigDecimal costWeight, BigDecimal efficiencyWeight) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<BalanceScorer.Card> cards = new ArrayList<>();
        for (CtScenario row : rows) {
            cards.add(card(row));
        }
        BalanceScorer.score(cards, costWeight, efficiencyWeight);
        for (int i = 0; i < rows.size(); i++) {
            CtScenario row = rows.get(i);
            BalanceScorer.Card scored = cards.get(i);
            row.setCostScore(scored.getCostScore());
            row.setEfficiencyScore(scored.getEfficiencyScore());
            row.setBalanceScore(scored.getBalanceScore());
            mergeScoresIntoResult(row);
            scenarioMapper.updateById(row);
        }
    }

    public String latestAutoRunNo() {
        CtScenario row = scenarioMapper.selectOne(new LambdaQueryWrapper<CtScenario>()
                .eq(CtScenario::getKind, "AUTO")
                .isNotNull(CtScenario::getRunNo)
                .orderByDesc(CtScenario::getId)
                .last("LIMIT 1"));
        return row == null ? null : row.getRunNo();
    }

    public List<CtScenario> listByRunNo(String runNo) {
        if (runNo == null || runNo.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return scenarioMapper.selectList(new LambdaQueryWrapper<CtScenario>()
                .eq(CtScenario::getRunNo, runNo)
                .orderByDesc(CtScenario::getBalanceScore)
                .orderByAsc(CtScenario::getId));
    }

    public CtScenario latestRecommendedAuto() {
        return scenarioMapper.selectOne(new LambdaQueryWrapper<CtScenario>()
                .eq(CtScenario::getKind, "AUTO")
                .eq(CtScenario::getRecommended, true)
                .orderByDesc(CtScenario::getId)
                .last("LIMIT 1"));
    }

    private CtScenario persist(
            String name,
            ScenarioParams params,
            boolean baseline,
            String kind,
            String runNo,
            boolean update,
            CtScenario existing) {
        params = params == null ? new ScenarioParams() : params.normalized();
        SandboxEngine.Result engineResult = engine.run(params, baselineData());
        CtScenario scenario = existing == null ? new CtScenario() : existing;
        if (scenario.getScenarioNo() == null) {
            scenario.setScenarioNo(codes.next("SC"));
        }
        scenario.setName(name);
        scenario.setBaseline(baseline);
        scenario.setKind(kind == null ? "MANUAL" : kind);
        scenario.setRunNo(runNo);
        scenario.setRecommended(Boolean.TRUE.equals(scenario.getRecommended()));
        scenario.setParamsJson(write(params));
        scenario.setResultJson(write(engineResult));
        scenario.setStatus("RUN");
        scenario.setTotalCost(engineResult.getTotalCost());
        scenario.setServiceLevel(engineResult.getServiceLevel());
        scenario.setAvgLeadDays(engineResult.getAvgLeadDays());
        scenario.setStockoutUnits(engineResult.getStockoutUnits());
        scoreAgainstBaseline(scenario, params);
        mergeScoresIntoResult(scenario);
        if (update) {
            scenarioMapper.updateById(scenario);
        } else {
            scenarioMapper.insert(scenario);
        }
        return scenario;
    }

    private void scoreAgainstBaseline(CtScenario scenario, ScenarioParams params) {
        CtScenario base = scenarioMapper.selectOne(new LambdaQueryWrapper<CtScenario>()
                .eq(CtScenario::getBaseline, true)
                .orderByAsc(CtScenario::getId)
                .last("LIMIT 1"));
        List<BalanceScorer.Card> cards = new ArrayList<>();
        BalanceScorer.Card current = card(scenario);
        cards.add(current);
        if (base != null && base.getId() != null && !base.getId().equals(scenario.getId())) {
            cards.add(card(base));
        }
        BalanceScorer.score(cards, params.getCostWeight(), params.getEfficiencyWeight());
        scenario.setCostScore(current.getCostScore());
        scenario.setEfficiencyScore(current.getEfficiencyScore());
        scenario.setBalanceScore(current.getBalanceScore());
    }

    private BalanceScorer.Card card(CtScenario scenario) {
        BalanceScorer.Card card = new BalanceScorer.Card();
        card.setTotalCost(nz(scenario.getTotalCost()));
        card.setServiceLevel(nz(scenario.getServiceLevel()));
        card.setAvgLeadDays(nz(scenario.getAvgLeadDays()));
        card.setStockoutUnits(nz(scenario.getStockoutUnits()));
        return card;
    }

    private CtAction dispatch(
            String type,
            String targetKey,
            Map<String, Object> params,
            BigDecimal expectedSaving,
            boolean execute) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("targetKey", targetKey);
        request.put("params", params);
        request.put("expectedSaving", expectedSaving);
        return execute ? actions.createAndExecute(request) : actions.createPending(request);
    }

    private List<OrderSnapshot> pendingOrders() {
        List<OrderSnapshot> result = new ArrayList<>();
        for (OrderSnapshot order : orderMapper.selectList(null)) {
            if (Arrays.asList("COMPLETED", "CANCELLED", "SHIPPED")
                    .contains(order.getStatus())) {
                continue;
            }
            result.add(order);
        }
        return result;
    }

    private List<ShipmentSnapshot> openShipments() {
        List<ShipmentSnapshot> result = new ArrayList<>();
        for (ShipmentSnapshot shipment : shipmentMapper.selectList(null)) {
            if (Arrays.asList("DELIVERED", "CLOSED", "CANCELLED")
                    .contains(shipment.getStatus())) {
                continue;
            }
            result.add(shipment);
        }
        return result;
    }

    private String preferredWarehouse(ScenarioParams params) {
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())) {
            return params.getSingleWarehouse() == null ? "WH-SH" : params.getSingleWarehouse();
        }
        BigDecimal costW = params.getCostWeight() == null
                ? BigDecimal.valueOf(0.5) : params.getCostWeight();
        BigDecimal effW = params.getEfficiencyWeight() == null
                ? BigDecimal.valueOf(0.5) : params.getEfficiencyWeight();
        if ("LOWEST_COST".equals(params.getAllocationStrategy())
                && costW.compareTo(effW) >= 0) {
            return "WH-SH";
        }
        return null;
    }

    private void mergeScoresIntoResult(CtScenario scenario) {
        Map<String, Object> result = result(scenario);
        result.put("costScore", scenario.getCostScore());
        result.put("efficiencyScore", scenario.getEfficiencyScore());
        result.put("balanceScore", scenario.getBalanceScore());
        scenario.setResultJson(write(result));
    }

    private String dominantCarrier(Map<String, BigDecimal> mix) {
        if (mix == null || mix.isEmpty()) {
            return null;
        }
        String best = null;
        BigDecimal bestWeight = BigDecimal.valueOf(-1);
        for (Map.Entry<String, BigDecimal> entry : mix.entrySet()) {
            BigDecimal weight = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            if (weight.compareTo(bestWeight) > 0) {
                best = entry.getKey();
                bestWeight = weight;
            }
        }
        return CarrierCodes.toTms(best);
    }

    private String firstSku() {
        List<String> skus = new ArrayList<>(baselineData().getDemandBySku().keySet());
        return skus.isEmpty() ? "SKU001" : skus.get(0);
    }

    private BigDecimal expectedSaving(CtScenario baseline, CtScenario scenario) {
        BigDecimal base = nz(baseline.getTotalCost());
        BigDecimal next = nz(scenario.getTotalCost());
        return base.subtract(next).max(BigDecimal.ZERO);
    }

    private Map<String, Object> job(
            String type,
            String targetKey,
            Map<String, Object> params,
            BigDecimal expectedSaving) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("targetKey", targetKey);
        row.put("params", params);
        row.put("expectedSaving", expectedSaving);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paramsOf(Map<String, Object> job) {
        Object params = job.get("params");
        return params instanceof Map ? (Map<String, Object>) params : new LinkedHashMap<>();
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
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
                        new LinkedHashMap<>(data.getRegionShare().getOrDefault(
                                "*", Collections.singletonMap("华东", BigDecimal.ONE))));
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
        delta.put("stockoutUnits", difference(scenario, baseline, "stockoutUnits"));
        delta.put("avgLeadDays", difference(scenario, baseline, "avgLeadDays"));
        delta.put("costScore", difference(scenario, baseline, "costScore"));
        delta.put("efficiencyScore", difference(scenario, baseline, "efficiencyScore"));
        delta.put("balanceScore", difference(scenario, baseline, "balanceScore"));
        return delta;
    }

    private BigDecimal difference(
            Map<String, Object> left,
            Map<String, Object> right,
            String key) {
        return decimal(left.getOrDefault(key, 0))
                .subtract(decimal(right.getOrDefault(key, 0)));
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
