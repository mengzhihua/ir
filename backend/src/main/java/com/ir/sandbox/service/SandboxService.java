package com.ir.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.common.CarrierCodes;
import com.ir.common.CodeGenerator;
import com.ir.common.WarehouseCodes;
import com.ir.sandbox.engine.AutoSandboxPicker;
import com.ir.sandbox.engine.BalanceScorer;
import com.ir.sandbox.engine.BaselineData;
import com.ir.sandbox.engine.CapitalProjection;
import com.ir.sandbox.engine.CapitalTiers;
import com.ir.sandbox.engine.SandboxEngine;
import com.ir.sandbox.engine.ScaleCatalog;
import com.ir.sandbox.engine.ScenarioParams;
import com.ir.sandbox.engine.ServiceFirstPicker;
import com.ir.sandbox.entity.CtScenario;
import com.ir.sandbox.mapper.CtScenarioMapper;
import com.ir.forecast.service.ForecastService;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final BalancePolicy policy;
    private final ForecastService forecasts;
    private final WmsOrderSnapshotMapper outboundMapper;

    @Autowired
    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            OrderSnapshotMapper orderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper,
            BalancePolicy policy,
            ForecastService forecasts,
            WmsOrderSnapshotMapper outboundMapper) {
        this.scenarioMapper = scenarioMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.engine = engine;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
        this.policy = policy;
        this.forecasts = forecasts;
        this.outboundMapper = outboundMapper;
    }

    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            OrderSnapshotMapper orderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            com.ir.snapshot.mapper.WmsOrderSnapshotMapper ignoredOutboundMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this(scenarioMapper, inventoryMapper, salesMapper, orderMapper,
                shipmentMapper, engine, actions, codes, objectMapper, null, null,
                ignoredOutboundMapper);
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
        Map<String, BigDecimal> carrierMix = rawCarrierMix(scenario.getParamsJson(),
                params.getCarrierMix());
        if (policy != null) {
            policy.updateReplenish(params.getSafetyDays(), params.getReplenishLeadDays());
        }
        Map<String, Object> scenarioResult = result(scenario);
        if (!scenarioResult.containsKey("skuWarehouse")
                || !scenarioResult.containsKey("stockoutByWarehouseSku")) {
            scenario = run(id);
            scenarioResult = result(scenario);
        }
        List<CtAction> result = new ArrayList<>();
        List<Map<String, Object>> jobs = new ArrayList<>();
        CtScenario baseline = scenarioMapper.selectOne(
                new LambdaQueryWrapper<CtScenario>()
                        .eq(CtScenario::getBaseline, true)
                        .orderByAsc(CtScenario::getId)
                        .last("LIMIT 1"));
        BigDecimal expected = baseline == null
                ? BigDecimal.ZERO : expectedSaving(baseline, scenario);

        Map<String, Object> skuWarehouse = mapValue(scenarioResult.get("skuWarehouse"));
        for (OrderSnapshot order : pendingOrders()) {
            if (jobs.size() >= 50) {
                break;
            }
            String sku = order.getSku();
            if ((sku == null || sku.isEmpty()) && outboundMapper != null) {
                for (WmsOrderSnapshot outbound : outboundMapper.selectList(null)) {
                    if (order.getOrderNo().equals(outbound.getExternalNo())
                            || order.getOrderNo().equals(outbound.getCode())) {
                        sku = outbound.getSku();
                        break;
                    }
                }
            }
            String target = sku == null ? null : stringValue(skuWarehouse.get(sku));
            if (target != null && !target.equals(order.getWarehouseCode())) {
                jobs.add(job("OMS_REROUTE_WAREHOUSE", order.getOrderNo(),
                        map("warehouseCode", target), null));
            }
        }

        List<ShipmentSnapshot> shipments = openShipments();
        Map<String, Integer> targets = carrierTargets(carrierMix,
                shipments.size());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ShipmentSnapshot shipment : shipments) {
            counts.merge(normalizeCarrier(shipment.getCarrierCode()),
                    1, Integer::sum);
        }
        for (ShipmentSnapshot shipment : shipments) {
            if (jobs.size() >= 50) {
                break;
            }
            String carrier = normalizeCarrier(shipment.getCarrierCode());
            if (counts.getOrDefault(carrier, 0) <= targets.getOrDefault(carrier, 0)) {
                continue;
            }
            String target = targetDeficit(counts, targets);
            if (target == null || target.equals(carrier)) {
                continue;
            }
            counts.put(carrier, counts.get(carrier) - 1);
            counts.put(target, counts.getOrDefault(target, 0) + 1);
            String canonicalTarget = normalizeCarrier(target);
            jobs.add(job("TMS_SWITCH_CARRIER", shipment.getWaybillCode(),
                    map("carrierCode", canonicalTarget),
                    BalanceAdvisor.freightSaving(carrier, canonicalTarget,
                            shipment.getFreightAmount())));
        }

        Map<String, Object> stockout = mapValue(scenarioResult.get("stockoutByWarehouseSku"));
        for (Map.Entry<String, Object> entry : stockout.entrySet()) {
            if (jobs.size() >= 50) {
                break;
            }
            BigDecimal qty = decimal(entry.getValue());
            if (qty.signum() <= 0 || !entry.getKey().contains("/")) {
                continue;
            }
            String[] parts = entry.getKey().split("/", 2);
            jobs.add(job("WMS_REPLENISH", entry.getKey(),
                    map("warehouseCode", parts[0], "sku", parts[1], "qty", qty), null));
        }

        if (forecasts != null) {
            List<Map<String, Object>> gaps = forecasts.replenish(
                    null, null, 14, params.getSafetyDays(), params.getReplenishLeadDays());
            for (Map<String, Object> row : gaps) {
                if (jobs.size() >= 50) {
                    break;
                }
                BigDecimal qty = decimal(row.get("suggestQty"));
                if (qty.signum() > 0) {
                    jobs.add(job("SRM_PURCHASE_SUGGEST", String.valueOf(row.get("sku")),
                            map("sku", row.get("sku"), "qty", qty,
                                    "supplier", row.get("supplier")), null));
                }
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

    public Map<String, Object> analyzeCapital(BigDecimal amount) {
        return analyzeCapital(amount, null, null);
    }

    public Map<String, Object> analyzeCapital(
            BigDecimal amount, Integer skuCount, BigDecimal inventoryQty) {
        return analyzeCapital(amount, skuCount, inventoryQty, true);
    }

    public Map<String, Object> analyzeCapital(
            BigDecimal amount, Integer skuCount, BigDecimal inventoryQty, boolean detailed) {
        return analyzeCapital(amount, catalog(skuCount, inventoryQty), detailed);
    }

    public Map<String, Object> analyzeCapital(
            BigDecimal amount, BaselineData data, boolean detailed) {
        long started = System.currentTimeMillis();
        BigDecimal capital = CapitalTiers.clampAmount(amount);
        if (data == null) {
            data = baselineData();
        }
        int skuCount = data.getDemandBySku().size();
        boolean large = skuCount > 200;
        int engineRuns = 0;
        SandboxEngine.Result normal = runCapital(data, capital, BigDecimal.ONE);
        engineRuns++;
        boolean stressProjected = CapitalProjection.canProjectStress(
                normal, skuCount, capital);
        SandboxEngine.Result stress;
        SandboxEngine.Result surge;
        if (stressProjected) {
            stress = CapitalProjection.scaleDemand(
                    normal, BigDecimal.valueOf(2), capital);
            surge = CapitalProjection.scaleDemand(
                    normal, BigDecimal.valueOf(5), capital);
        } else {
            stress = runCapital(data, capital, BigDecimal.valueOf(2));
            surge = runCapital(data, capital, BigDecimal.valueOf(5));
            engineRuns += 2;
        }
        BigDecimal minReliable;
        int maxMultiplier;
        if (detailed && !large) {
            minReliable = minReliableCapital(data, capital);
            maxMultiplier = maxReliableMultiplier(data, capital);
        } else {
            minReliable = "RELIABLE".equals(normal.getCapitalVerdict())
                    ? nz(normal.getCashUsed()).max(BigDecimal.ONE) : capital;
            maxMultiplier = "RELIABLE".equals(surge.getCapitalVerdict())
                    ? 5
                    : "RELIABLE".equals(stress.getCapitalVerdict()) ? 2 : 1;
        }
        BigDecimal used = nz(normal.getCashUsed());
        BigDecimal headroom = used.signum() == 0
                ? BigDecimal.ZERO
                : capital.divide(used, 2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workingCapital", capital);
        result.put("label", CapitalTiers.labelOf(capital));
        result.put("skuCount", normal.getSkuCount());
        result.put("inventoryUnits", normal.getInventoryUnits());
        result.put("inventoryValue", normal.getInventoryValue());
        result.put("baseline", capitalView(normal));
        result.put("demand2x", capitalView(stress));
        result.put("demand5x", capitalView(surge));
        result.put("headroom", headroom);
        result.put("minReliableCapital", minReliable);
        result.put("maxReliableDemandMultiplier", maxMultiplier);
        String verdict = overallVerdict(normal, stress);
        result.put("verdict", verdict);
        result.put("reliable", "RELIABLE".equals(verdict));
        result.put("reason", overallReason(normal, stress, surge, capital, headroom, minReliable));
        int[] extraRuns = new int[1];
        List<Map<String, Object>> playbook = playbook(data, capital, normal, detailed, extraRuns);
        engineRuns += extraRuns[0];
        Map<String, Object> recommended = pickPlay(playbook);
        result.put("playbook", playbook);
        result.put("recommended", recommended);
        result.put("optimizations", optimizations(
                normal, stress, surge, capital, minReliable, maxMultiplier, headroom,
                recommended, playbook, stressProjected));
        result.put("elapsedMs", System.currentTimeMillis() - started);
        result.put("horizonDays", capitalHorizon(data));
        result.put("engineRuns", engineRuns);
        result.put("stressProjected", stressProjected);
        return result;
    }

    public Map<String, Object> sweepCapital(
            List<BigDecimal> extraAmounts, Integer skuCount, BigDecimal inventoryQty) {
        long startedAll = System.currentTimeMillis();
        List<BigDecimal> amounts = new ArrayList<BigDecimal>(CapitalTiers.amounts());
        if (extraAmounts != null) {
            for (BigDecimal extra : extraAmounts) {
                if (extra == null || extra.signum() <= 0) {
                    continue;
                }
                BigDecimal clamped = CapitalTiers.clampAmount(extra);
                if (!containsAmount(amounts, clamped)) {
                    amounts.add(clamped);
                }
            }
        }
        amounts.sort(BigDecimal::compareTo);
        BaselineData data = catalog(skuCount, inventoryQty);
        BigDecimal ceiling = amounts.get(amounts.size() - 1);
        Map<String, Object> unconstrained = analyzeCapital(ceiling, data, false);
        BigDecimal cash1x = decimal(nested(unconstrained, "baseline", "cashUsed"));
        int engineRuns = intVal(unconstrained.get("engineRuns"), 1);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<String> issues = new ArrayList<String>();
        boolean flowOk = true;
        String previousVerdict = null;
        BigDecimal previousAmount = null;
        for (BigDecimal amount : amounts) {
            long started = System.currentTimeMillis();
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("workingCapital", amount);
            row.put("label", CapitalTiers.labelOf(amount));
            try {
                Map<String, Object> analysis;
                boolean projected = amount.compareTo(cash1x) >= 0
                        && amount.compareTo(ceiling) < 0;
                if (amount.compareTo(ceiling) == 0) {
                    analysis = unconstrained;
                    row.put("projected", false);
                } else if (projected) {
                    analysis = projectCapital(unconstrained, amount);
                    row.put("projected", true);
                } else {
                    analysis = analyzeCapital(amount, data, false);
                    engineRuns += intVal(analysis.get("engineRuns"), 1);
                    row.put("projected", false);
                }
                row.put("verdict", analysis.get("verdict"));
                row.put("reliable", analysis.get("reliable"));
                row.put("reason", analysis.get("reason"));
                row.put("cashUsed", nested(analysis, "baseline", "cashUsed"));
                row.put("serviceLevel", nested(analysis, "baseline", "serviceLevel"));
                row.put("stockoutUnits", nested(analysis, "baseline", "stockoutUnits"));
                row.put("capitalUtilization", nested(analysis, "baseline", "capitalUtilization"));
                row.put("headroom", analysis.get("headroom"));
                row.put("minReliableCapital", analysis.get("minReliableCapital"));
                row.put("skuCount", analysis.get("skuCount"));
                row.put("inventoryUnits", analysis.get("inventoryUnits"));
                row.put("inventoryValue", analysis.get("inventoryValue"));
                row.put("recommendedName", nested(analysis, "recommended", "name"));
                row.put("recommendedLead", nested(analysis, "recommended", "replenishLeadDays"));
                long rowMs = System.currentTimeMillis() - started;
                if (amount.compareTo(ceiling) == 0) {
                    rowMs = Math.max(rowMs, intVal(analysis.get("elapsedMs"), 0));
                }
                row.put("elapsedMs", rowMs);
                List<String> local = validateTier(analysis, amount);
                row.put("issues", local);
                if (!local.isEmpty()) {
                    flowOk = false;
                    issues.addAll(local);
                }
                String verdict = String.valueOf(analysis.get("verdict"));
                if (previousVerdict != null
                        && "RELIABLE".equals(previousVerdict)
                        && !"RELIABLE".equals(verdict)
                        && previousAmount.compareTo(amount) < 0) {
                    String msg = CapitalTiers.labelOf(previousAmount)
                            + " 可靠但更大的 "
                            + CapitalTiers.labelOf(amount)
                            + " 反而不可靠";
                    flowOk = false;
                    issues.add(msg);
                    @SuppressWarnings("unchecked")
                    List<String> rowIssues = (List<String>) row.get("issues");
                    rowIssues.add(msg);
                }
                previousVerdict = verdict;
                previousAmount = amount;
            } catch (RuntimeException ex) {
                flowOk = false;
                String msg = CapitalTiers.labelOf(amount) + " 推演失败: " + ex.getMessage();
                issues.add(msg);
                row.put("verdict", "ERROR");
                row.put("reliable", false);
                row.put("issues", Collections.singletonList(msg));
                row.put("elapsedMs", System.currentTimeMillis() - started);
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tiers", CapitalTiers.presets());
        result.put("rows", rows);
        result.put("issues", issues);
        result.put("flowOk", flowOk);
        result.put("skuCount", skuCount == null || skuCount <= 0
                ? snapshotSkuCount() : CapitalTiers.clampSku(skuCount));
        result.put("inventoryQty", inventoryQty == null ? null : CapitalTiers.clampQty(inventoryQty));
        result.put("elapsedMs", System.currentTimeMillis() - startedAll);
        result.put("engineRuns", engineRuns);
        result.put("unconstrainedCash", cash1x);
        return result;
    }

    public CtScenario adoptRecommended(BigDecimal amount) {
        ensureBaseline();
        Map<String, Object> analysis = analyzeCapital(amount);
        @SuppressWarnings("unchecked")
        Map<String, Object> recommended = (Map<String, Object>) analysis.get("recommended");
        if (recommended == null || recommended.get("name") == null) {
            throw new IllegalStateException("没有可采纳的资金盘策略");
        }
        BigDecimal capital = CapitalTiers.clampAmount(amount);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> mix = recommended.get("carrierMix") instanceof Map
                ? (Map<String, BigDecimal>) recommended.get("carrierMix")
                : null;
        ScenarioParams params = capitalParams(
                capital,
                String.valueOf(recommended.getOrDefault("allocationStrategy", "BALANCED")),
                decimal(recommended.get("safetyDays")).intValue(),
                decimal(recommended.get("replenishLeadDays")).intValue(),
                BigDecimal.ONE,
                mix);
        if (policy != null) {
            policy.updateReplenish(params.getSafetyDays(), params.getReplenishLeadDays());
        }
        return persist("资金盘推荐·" + recommended.get("name")
                + "·" + CapitalTiers.labelOf(capital), params, false, "MANUAL", null, false, null);
    }

    public Map<String, Object> resultOf(CtScenario scenario) {
        return result(scenario);
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
                .orderByDesc(CtScenario::getRecommended)
                .orderByAsc(CtScenario::getId));
    }

    public BigDecimal cashUsedOf(CtScenario scenario) {
        return decimal(resultOf(scenario).get("cashUsed"));
    }

    public boolean stressReliableOf(CtScenario scenario) {
        return Boolean.TRUE.equals(resultOf(scenario).get("stressReliable"));
    }

    public List<String> warehouseCodes() {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<String>();
        for (com.ir.snapshot.entity.InventorySnapshot item : inventoryMapper.selectList(null)) {
            if (item.getWarehouseCode() == null || item.getWarehouseCode().trim().isEmpty()) {
                continue;
            }
            codes.add(WarehouseCodes.toOms(item.getWarehouseCode().trim()));
        }
        return new ArrayList<String>(codes);
    }

    public void annotateCandidate(CtScenario scenario, String source, String signal) {
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("candidateSource", source);
        extra.put("candidateSignal", signal);
        mergeIntoResult(scenario, extra);
    }

    public void attachStress(CtScenario scenario, BaselineData data) {
        if (nz(scenario.getServiceLevel()).compareTo(ServiceFirstPicker.MIN_SERVICE) < 0
                || nz(scenario.getStockoutUnits()).signum() > 0) {
            Map<String, Object> extra = new LinkedHashMap<String, Object>();
            extra.put("stressDemandMultiplier", 2);
            extra.put("stressSkipped", true);
            extra.put("stressReliable", false);
            mergeIntoResult(scenario, extra);
            return;
        }
        ScenarioParams params = paramsOf(scenario);
        if (params == null) {
            params = new ScenarioParams();
        }
        params = params.normalized();
        params.setDemandMultiplier(BigDecimal.valueOf(2));
        SandboxEngine.Result stress = engine.run(params, data == null ? baselineData() : data);
        boolean reliable = AutoSandboxPicker.stressReliable(
                stress.getServiceLevel(), stress.getStockoutUnits());
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("stressDemandMultiplier", 2);
        extra.put("stressSkipped", false);
        extra.put("stressServiceLevel", stress.getServiceLevel());
        extra.put("stressStockoutUnits", stress.getStockoutUnits());
        extra.put("stressCashUsed", stress.getCashUsed());
        extra.put("stressCapitalVerdict", stress.getCapitalVerdict());
        extra.put("stressReliable", reliable);
        mergeIntoResult(scenario, extra);
    }

    public void mergeIntoResult(CtScenario scenario, Map<String, Object> extra) {
        if (scenario == null || extra == null || extra.isEmpty()) {
            return;
        }
        Map<String, Object> result = result(scenario);
        result.putAll(extra);
        scenario.setResultJson(write(result));
        if (scenario.getId() != null) {
            scenarioMapper.updateById(scenario);
        }
    }

    public List<Map<String, Object>> listAutoHistory(int size) {
        int limit = size < 1 ? 8 : Math.min(size, 30);
        List<CtScenario> rows = scenarioMapper.selectList(new LambdaQueryWrapper<CtScenario>()
                .eq(CtScenario::getKind, "AUTO")
                .eq(CtScenario::getRecommended, true)
                .orderByDesc(CtScenario::getId)
                .last("LIMIT " + limit));
        List<Map<String, Object>> history = new ArrayList<Map<String, Object>>();
        for (CtScenario row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            Map<String, Object> result = resultOf(row);
            item.put("id", row.getId());
            item.put("runNo", row.getRunNo());
            item.put("name", row.getName());
            item.put("serviceLevel", row.getServiceLevel());
            item.put("stockoutUnits", row.getStockoutUnits());
            item.put("totalCost", row.getTotalCost());
            item.put("cashUsed", result.get("cashUsed"));
            item.put("stressReliable", result.get("stressReliable"));
            item.put("pickRationale", result.get("pickRationale"));
            item.put("createdAt", row.getCreatedAt());
            history.add(item);
        }
        return history;
    }

    public ScenarioParams paramsOf(CtScenario scenario) {
        if (scenario == null || scenario.getParamsJson() == null) {
            return new ScenarioParams();
        }
        return read(scenario.getParamsJson(), ScenarioParams.class);
    }

    public ScenarioParams manualDefaults() {
        ScenarioParams params = new ScenarioParams();
        CtScenario recommended = latestRecommendedAuto();
        if (recommended == null || recommended.getParamsJson() == null) {
            return params;
        }
        ScenarioParams source = read(recommended.getParamsJson(), ScenarioParams.class);
        params.setSafetyDays(source.getSafetyDays());
        params.setReplenishLeadDays(source.getReplenishLeadDays());
        params.setAllocationStrategy(source.getAllocationStrategy());
        if (source.getCarrierMix() != null && !source.getCarrierMix().isEmpty()) {
            params.setCarrierMix(source.getCarrierMix());
        }
        return params;
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
        List<OrderSnapshot> rows = orderMapper.selectList(new LambdaQueryWrapper<>());
        if (rows == null) {
            return result;
        }
        for (OrderSnapshot order : rows) {
            if (!Arrays.asList("CREATED", "AUDITED", "ALLOCATED")
                    .contains(order.getStatus())) {
                continue;
            }
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
        List<ShipmentSnapshot> rows = shipmentMapper.selectList(new LambdaQueryWrapper<>());
        if (rows == null) {
            return result;
        }
        for (ShipmentSnapshot shipment : rows) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value
                : new LinkedHashMap<>();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Integer> carrierTargets(
            Map<String, BigDecimal> mix, int total) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        if (mix == null || mix.isEmpty() || total <= 0) {
            return targets;
        }
        BigDecimal sum = mix.values().stream()
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0) {
            return targets;
        }
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : mix.entrySet()) {
            String carrier = normalizeCarrier(entry.getKey());
            normalized.merge(carrier, entry.getValue() == null
                    ? BigDecimal.ZERO : entry.getValue(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(normalized.entrySet());
        List<BigDecimal> remainders = new ArrayList<>();
        int assigned = 0;
        for (Map.Entry<String, BigDecimal> entry : entries) {
            BigDecimal exact = (entry.getValue() == null ? BigDecimal.ZERO
                    : entry.getValue()).divide(sum, 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(total));
            int floor = exact.setScale(0, RoundingMode.FLOOR).intValue();
            targets.put(entry.getKey(), floor);
            remainders.add(exact.subtract(BigDecimal.valueOf(floor)));
            assigned += floor;
        }
        while (assigned < total) {
            int best = 0;
            for (int index = 1; index < entries.size(); index++) {
                if (remainders.get(index).compareTo(remainders.get(best)) > 0
                        || (remainders.get(index).compareTo(remainders.get(best)) == 0
                        && entries.get(index).getValue().compareTo(
                        entries.get(best).getValue()) > 0)) {
                    best = index;
                }
            }
            String carrier = entries.get(best).getKey();
            targets.put(carrier, targets.get(carrier) + 1);
            remainders.set(best, BigDecimal.valueOf(-1));
            assigned++;
        }
        return targets;
    }

    private String normalizeCarrier(String code) {
        if (code == null || code.trim().isEmpty()) {
            return CarrierCodes.SELF01;
        }
        String value = code.trim().toUpperCase();
        if ("SF".equals(value) || "SFEXPRESS".equals(value)
                || value.contains("顺丰")
                || "JD".equals(value) || "JDL".equals(value)
                || "JINGDONG".equals(value) || value.contains("京东")
                || "SELF".equals(value) || "SELF01".equals(value)
                || "FLEET".equals(value) || value.contains("自建")
                || value.contains("车队")
                || "ZTO".equals(value) || "YTO".equals(value)
                || "STO".equals(value) || "YUNDA".equals(value)) {
            return CarrierCodes.toTms(value);
        }
        return value;
    }

    private Map<String, BigDecimal> rawCarrierMix(
            String json, Map<String, BigDecimal> fallback) {
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return fallback;
        }
        Object value = raw.get("carrierMix");
        if (!(value instanceof Map)) {
            return fallback;
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            try {
                result.put(String.valueOf(entry.getKey()),
                        new BigDecimal(String.valueOf(entry.getValue())));
            } catch (NumberFormatException ignored) {
                // ignore malformed share
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    private String targetDeficit(
            Map<String, Integer> counts, Map<String, Integer> targets) {
        String selected = null;
        int deficit = 0;
        for (Map.Entry<String, Integer> target : targets.entrySet()) {
            int current = counts.getOrDefault(target.getKey(), 0);
            int gap = target.getValue() - current;
            if (gap > deficit) {
                deficit = gap;
                selected = target.getKey();
            }
        }
        return selected;
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
        normalizeShares(data.getChannelShare());
        normalizeShares(data.getRegionShare());
        for (String sku : data.getDemandBySku().keySet()) {
            if (!data.getRegionShare().containsKey(sku)) {
                data.getRegionShare().put(sku,
                        new LinkedHashMap<>(data.getRegionShare().getOrDefault(
                                "*", Collections.singletonMap("华东", BigDecimal.ONE))));
            }
        }
        return data;
    }

    public void addShare(
            Map<String, Map<String, BigDecimal>> shares,
            String key,
            String dimension,
            BigDecimal amount) {
        Map<String, BigDecimal> values = shares.computeIfAbsent(
                key, ignored -> new LinkedHashMap<>());
        values.put(dimension, values.getOrDefault(dimension,
                BigDecimal.ZERO).add(amount));
    }

    public void normalizeShares(
            Map<String, Map<String, BigDecimal>> shares) {
        for (Map<String, BigDecimal> values : shares.values()) {
            BigDecimal total = values.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            objectMapper.findAndRegisterModules();
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("场景结果保存失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(CtScenario scenario) {
        return read(scenario.getResultJson(), Map.class);
    }

    private Map<String, Object> capitalView(SandboxEngine.Result result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("totalCost", result.getTotalCost());
        row.put("serviceLevel", result.getServiceLevel());
        row.put("stockoutUnits", result.getStockoutUnits());
        row.put("avgLeadDays", result.getAvgLeadDays());
        row.put("workingCapital", result.getWorkingCapital());
        row.put("cashUsed", result.getCashUsed());
        row.put("cashRemaining", result.getCashRemaining());
        row.put("purchaseCash", result.getPurchaseCash());
        row.put("opsCash", result.getOpsCash());
        row.put("deferredPurchaseQty", result.getDeferredPurchaseQty());
        row.put("capitalShortage", result.getCapitalShortage());
        row.put("capitalUtilization", result.getCapitalUtilization());
        row.put("capitalFeasible", result.getCapitalFeasible());
        row.put("capitalVerdict", result.getCapitalVerdict());
        row.put("capitalReason", result.getCapitalReason());
        row.put("skuCount", result.getSkuCount());
        row.put("inventoryUnits", result.getInventoryUnits());
        row.put("inventoryValue", result.getInventoryValue());
        return row;
    }

    private BaselineData catalog(Integer skuCount, BigDecimal inventoryQty) {
        if (skuCount == null || skuCount <= 0) {
            return baselineData();
        }
        return ScaleCatalog.build(skuCount, inventoryQty);
    }

    private int snapshotSkuCount() {
        return baselineData().getDemandBySku().size();
    }

    private Object nested(Map<String, Object> root, String a, String b) {
        Object layer = root.get(a);
        if (!(layer instanceof Map)) {
            return null;
        }
        return ((Map<?, ?>) layer).get(b);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectCapital(Map<String, Object> rich, BigDecimal amount) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(rich);
        result.put("workingCapital", amount);
        result.put("label", CapitalTiers.labelOf(amount));
        Map<String, Object> baseline = projectView(
                (Map<String, Object>) rich.get("baseline"), amount);
        Map<String, Object> demand2x = projectView(
                (Map<String, Object>) rich.get("demand2x"), amount);
        Map<String, Object> demand5x = projectView(
                (Map<String, Object>) rich.get("demand5x"), amount);
        result.put("baseline", baseline);
        result.put("demand2x", demand2x);
        result.put("demand5x", demand5x);
        BigDecimal used = decimal(baseline.get("cashUsed"));
        BigDecimal headroom = used.signum() == 0
                ? BigDecimal.ZERO
                : amount.divide(used, 2, RoundingMode.HALF_UP);
        result.put("headroom", headroom);
        String verdict = projectedVerdict(baseline, demand2x);
        result.put("verdict", verdict);
        result.put("reliable", "RELIABLE".equals(verdict));
        result.put("reason", "资金盘 " + amount.toPlainString()
                + " 结论" + verdictLabel(verdict)
                + "：由无约束推演投影，常态占用 "
                + baseline.get("capitalUtilization")
                + "，2 倍需求占用 "
                + demand2x.get("capitalUtilization")
                + "，5 倍需求占用 "
                + demand5x.get("capitalUtilization")
                + "，安全垫约 " + headroom.toPlainString() + " 倍");
        result.put("engineRuns", 0);
        return result;
    }

    private Map<String, Object> projectView(Map<String, Object> source, BigDecimal amount) {
        Map<String, Object> row = source == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(source);
        BigDecimal used = decimal(row.get("cashUsed"));
        BigDecimal util = amount.signum() == 0
                ? BigDecimal.ONE
                : used.divide(amount, 6, RoundingMode.HALF_UP);
        boolean feasible = used.compareTo(amount) <= 0
                && decimal(row.get("deferredPurchaseQty")).signum() == 0
                && decimal(row.get("capitalShortage")).signum() == 0;
        row.put("workingCapital", amount);
        row.put("cashRemaining", amount.subtract(used).max(BigDecimal.ZERO));
        row.put("capitalUtilization", util.setScale(4, RoundingMode.HALF_UP));
        row.put("capitalFeasible", feasible);
        if (!feasible) {
            row.put("capitalVerdict", "INSUFFICIENT");
            row.put("capitalReason", "资金盘覆盖不了采购或履约现金，补货被推迟或出现现金缺口");
        } else if (util.compareTo(new BigDecimal("0.80")) >= 0) {
            row.put("capitalVerdict", "TIGHT");
            row.put("capitalReason", "资金盘能撑住，但现金占用已超过 80%");
        } else {
            row.put("capitalVerdict", "RELIABLE");
            row.put("capitalReason", "资金盘覆盖履约与补货现金，占用低于 80%");
        }
        return row;
    }

    private String projectedVerdict(Map<String, Object> normal, Map<String, Object> stress) {
        String first = normal == null ? null : String.valueOf(normal.get("capitalVerdict"));
        if (!"RELIABLE".equals(first)) {
            return first;
        }
        String second = stress == null ? null : String.valueOf(stress.get("capitalVerdict"));
        if ("INSUFFICIENT".equals(second)) {
            return "TIGHT";
        }
        return second;
    }

    private List<String> validateTier(Map<String, Object> analysis, BigDecimal amount) {
        List<String> issues = new ArrayList<String>();
        String label = CapitalTiers.labelOf(amount);
        if (analysis.get("verdict") == null) {
            issues.add(label + " 没有资金结论");
        }
        BigDecimal cash = decimal(nested(analysis, "baseline", "cashUsed"));
        BigDecimal service = decimal(nested(analysis, "baseline", "serviceLevel"));
        BigDecimal stockout = decimal(nested(analysis, "baseline", "stockoutUnits"));
        BigDecimal util = decimal(nested(analysis, "baseline", "capitalUtilization"));
        if (cash.signum() < 0) {
            issues.add(label + " 占用现金为负");
        }
        if (stockout.signum() < 0) {
            issues.add(label + " 缺货件数为负");
        }
        if (service.signum() < 0 || service.compareTo(BigDecimal.ONE) > 0) {
            issues.add(label + " 服务水平越界: " + service);
        }
        if (util.signum() < 0) {
            issues.add(label + " 资金占用率为负");
        }
        if (Boolean.TRUE.equals(analysis.get("reliable")) && util.compareTo(BigDecimal.ONE) > 0) {
            issues.add(label + " 标为可靠但占用超过资金盘");
        }
        if (nested(analysis, "recommended", "name") == null) {
            issues.add(label + " 没有推荐策略");
        }
        return issues;
    }

    private String overallVerdict(SandboxEngine.Result normal, SandboxEngine.Result stress) {
        if (!"RELIABLE".equals(normal.getCapitalVerdict())) {
            return normal.getCapitalVerdict();
        }
        if ("INSUFFICIENT".equals(stress.getCapitalVerdict())) {
            return "TIGHT";
        }
        return stress.getCapitalVerdict();
    }

    private List<Map<String, Object>> playbook(
            BaselineData data,
            BigDecimal capital,
            SandboxEngine.Result normal,
            boolean detailed,
            int[] extraRuns) {
        int n = data.getDemandBySku().size();
        boolean compact = n >= CapitalProjection.LARGE_SKU || !detailed;
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(playFromResult(normal, capital, "就近默认",
                capitalParams(capital, "NEAREST", 3, 3, BigDecimal.ONE, null)));
        if (!compact) {
            rows.add(play(data, capital, extraRuns, "短交期补货",
                    capitalParams(capital, "BALANCED", 3, 1, BigDecimal.ONE, null)));
        }
        rows.add(play(data, capital, extraRuns, "低安全短交期",
                capitalParams(capital, "BALANCED", 1, 1, BigDecimal.ONE, null)));
        if (!compact && n < 5000) {
            rows.add(play(data, capital, extraRuns, "低安全库存",
                    capitalParams(capital, "BALANCED", 1, 3, BigDecimal.ONE, null)));
            rows.add(play(data, capital, extraRuns, "经济承运",
                    capitalParams(capital, "LOWEST_COST", 3, 4, BigDecimal.ONE, economicMix())));
        }
        if (!compact && n < 1000) {
            rows.add(play(data, capital, extraRuns, "低安全短交期·5倍需求",
                    capitalParams(capital, "BALANCED", 1, 1, BigDecimal.valueOf(5), null)));
        }
        return rows;
    }

    private Map<String, Object> play(
            BaselineData data,
            BigDecimal capital,
            int[] extraRuns,
            String name,
            ScenarioParams params) {
        params.setHorizonDays(capitalHorizon(data));
        SandboxEngine.Result result = engine.run(params, data);
        if (extraRuns != null && extraRuns.length > 0) {
            extraRuns[0]++;
        }
        return decoratePlay(result, capital, name, params);
    }

    private Map<String, Object> playFromResult(
            SandboxEngine.Result result,
            BigDecimal capital,
            String name,
            ScenarioParams params) {
        return decoratePlay(result, capital, name, params);
    }

    private Map<String, Object> decoratePlay(
            SandboxEngine.Result result,
            BigDecimal capital,
            String name,
            ScenarioParams params) {
        Map<String, Object> row = capitalView(result);
        row.put("name", name);
        row.put("allocationStrategy", params.getAllocationStrategy());
        row.put("safetyDays", params.getSafetyDays());
        row.put("replenishLeadDays", params.getReplenishLeadDays());
        row.put("demandMultiplier", params.getDemandMultiplier());
        row.put("carrierMix", params.getCarrierMix());
        row.put("workingCapital", capital);
        row.put("recommended", false);
        return row;
    }

    private ScenarioParams capitalParams(
            BigDecimal capital,
            String allocation,
            int safetyDays,
            int leadDays,
            BigDecimal multiplier,
            Map<String, BigDecimal> mix) {
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(30);
        params.setWorkingCapital(capital);
        params.setAllocationStrategy(allocation);
        params.setSafetyDays(safetyDays);
        params.setReplenishLeadDays(leadDays);
        params.setDemandMultiplier(multiplier);
        if (mix != null) {
            params.setCarrierMix(mix);
        }
        return params;
    }

    private Map<String, BigDecimal> economicMix() {
        Map<String, BigDecimal> mix = new LinkedHashMap<>();
        mix.put("SF", BigDecimal.valueOf(0.1));
        mix.put("JD", BigDecimal.valueOf(0.3));
        mix.put("SELF01", BigDecimal.valueOf(0.6));
        return mix;
    }

    private Map<String, Object> pickPlay(List<Map<String, Object>> playbook) {
        List<Map<String, Object>> baselinePlays = new ArrayList<>();
        for (Map<String, Object> row : playbook) {
            if (decimal(row.get("demandMultiplier")).compareTo(BigDecimal.ONE) == 0) {
                baselinePlays.add(row);
            }
        }
        Map<String, Object> best = ServiceFirstPicker.pickByCash(
                baselinePlays,
                row -> decimal(row.get("serviceLevel")),
                row -> decimal(row.get("stockoutUnits")),
                row -> decimal(row.get("cashUsed")));
        if (best == null && !playbook.isEmpty()) {
            best = playbook.get(0);
        }
        if (best != null) {
            best.put("recommended", true);
        }
        return best;
    }

    private SandboxEngine.Result runCapital(
            BaselineData data, BigDecimal capital, BigDecimal multiplier) {
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(capitalHorizon(data));
        params.setWorkingCapital(capital);
        params.setDemandMultiplier(multiplier);
        return engine.run(params, data);
    }

    private int capitalHorizon(BaselineData data) {
        int n = data == null || data.getDemandBySku() == null ? 0 : data.getDemandBySku().size();
        if (n >= 10000) {
            return 3;
        }
        if (n >= 1000) {
            return 7;
        }
        if (n >= 200) {
            return 14;
        }
        return 30;
    }

    private boolean containsAmount(List<BigDecimal> amounts, BigDecimal extra) {
        for (BigDecimal amount : amounts) {
            if (amount.compareTo(extra) == 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal minReliableCapital(BaselineData data, BigDecimal capital) {
        if (!"RELIABLE".equals(overallVerdict(
                runCapital(data, capital, BigDecimal.ONE),
                runCapital(data, capital, BigDecimal.valueOf(2))))) {
            return capital;
        }
        BigDecimal low = BigDecimal.ONE;
        BigDecimal high = capital;
        for (int i = 0; i < 16; i++) {
            BigDecimal mid = low.add(high).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
            if (mid.compareTo(low) <= 0 || mid.compareTo(high) >= 0) {
                break;
            }
            if ("RELIABLE".equals(overallVerdict(
                    runCapital(data, mid, BigDecimal.ONE),
                    runCapital(data, mid, BigDecimal.valueOf(2))))) {
                high = mid;
            } else {
                low = mid.add(BigDecimal.ONE);
            }
        }
        return high;
    }

    private int maxReliableMultiplier(BaselineData data, BigDecimal capital) {
        int best = 1;
        int low = 1;
        int high = 80;
        while (low <= high) {
            int mid = (low + high) / 2;
            SandboxEngine.Result result = runCapital(data, capital, BigDecimal.valueOf(mid));
            if ("RELIABLE".equals(result.getCapitalVerdict())) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private List<String> optimizations(
            SandboxEngine.Result normal,
            SandboxEngine.Result stress,
            SandboxEngine.Result surge,
            BigDecimal capital,
            BigDecimal minReliable,
            int maxMultiplier,
            BigDecimal headroom,
            Map<String, Object> recommended,
            List<Map<String, Object>> playbook,
            boolean stressProjected) {
        List<String> rows = new ArrayList<>();
        BigDecimal inventoryValue = nz(normal.getInventoryValue());
        if (inventoryValue.compareTo(capital) > 0) {
            rows.add("库存金额 " + inventoryValue.toPlainString()
                    + " 已超过资金盘 " + capital.toPlainString()
                    + "，大规模下应先降低单 SKU 库存或提高资金档位，否则补货会被推迟。");
        }
        if (normal.getSkuCount() >= 10000) {
            rows.add("SKU 规模 " + normal.getSkuCount()
                    + "：只对已有库存的仓库补货，量级扫描复用无约束结果，避免每个档位全量重算。");
        }
        if (stressProjected) {
            rows.add("常态可靠、零缺货且 5 倍现金仍低于资金盘 80%，2 倍/5 倍需求按线性投影，策略册只加跑低安全短交期。");
        }
        if (recommended != null && recommended.get("name") != null) {
            rows.add("推荐策略「" + recommended.get("name")
                    + "」：安全天数 " + recommended.get("safetyDays")
                    + "、补货提前期 " + recommended.get("replenishLeadDays")
                    + " 天，30 天现金 "
                    + recommended.get("cashUsed")
                    + "，服务水平 "
                    + recommended.get("serviceLevel") + "。");
        }
        if (recommended != null && playbook != null && !playbook.isEmpty()) {
            BigDecimal baselineCash = decimal(playbook.get(0).get("cashUsed"));
            BigDecimal recommendedCash = decimal(recommended.get("cashUsed"));
            BigDecimal saved = baselineCash.subtract(recommendedCash);
            if (baselineCash.signum() > 0 && saved.signum() > 0) {
                rows.add("相对就近默认可少占用现金 "
                        + saved.toPlainString()
                        + "，约 "
                        + saved.multiply(new BigDecimal("100"))
                                .divide(baselineCash, 1, RoundingMode.HALF_UP)
                        + "%；只降安全库存不缩短交期会掉服务水平。");
            }
            for (Map<String, Object> row : playbook) {
                if ("低安全短交期".equals(row.get("name"))
                        && decimal(row.get("stockoutUnits")).signum() > 0
                        && !Boolean.TRUE.equals(row.get("recommended"))) {
                    rows.add("当前库存撑不住再把安全天数降到 1，会缺货 "
                            + row.get("stockoutUnits")
                            + "，先保短交期补货（安全 3 天 + 提前期 1 天）。");
                }
            }
        }
        if (headroom.compareTo(BigDecimal.valueOf(20)) >= 0) {
            rows.add(CapitalTiers.labelOf(capital) + " 相对当前履约现金需求过大，约 "
                    + minReliable.toPlainString()
                    + " 即可覆盖常态和 2 倍需求并保持可靠。");
        }
        BigDecimal used = nz(normal.getCashUsed());
        if (used.signum() > 0
                && nz(normal.getPurchaseCash()).divide(used, 4, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.70")) >= 0) {
            rows.add("现金主要被补货占用，缩短补货提前期或降低安全天数，比换承运商更能释放资金盘。");
        }
        if ("RELIABLE".equals(surge.getCapitalVerdict())) {
            rows.add("需求放大 5 倍仍可靠，资金不是当前履约瓶颈，优化重点应放在仓配时效和缺货。");
        }
        if (maxMultiplier >= 80) {
            rows.add("需求倍率搜索上界为 80，这是搜索上限而不是实测失稳点。");
        } else if (maxMultiplier >= 10) {
            rows.add("按当前费率，资金盘大约还能撑到 " + maxMultiplier + " 倍需求才开始吃紧。");
        }
        if (rows.isEmpty()) {
            rows.add("资金盘余量有限，优先保障补货现金，避免把安全库存再抬高。");
        }
        return rows;
    }

    private String overallReason(
            SandboxEngine.Result normal,
            SandboxEngine.Result stress,
            SandboxEngine.Result surge,
            BigDecimal capital,
            BigDecimal headroom,
            BigDecimal minReliable) {
        return "资金盘 " + capital.toPlainString()
                + " 结论" + verdictLabel(overallVerdict(normal, stress))
                + "：常态占用 " + normal.getCapitalUtilization()
                + "，2 倍需求占用 " + stress.getCapitalUtilization()
                + "，5 倍需求占用 " + surge.getCapitalUtilization()
                + "，安全垫约 " + headroom.toPlainString()
                + " 倍，覆盖常态+翻倍的最低可靠资金约 "
                + minReliable.toPlainString();
    }

    private String verdictLabel(String verdict) {
        if ("RELIABLE".equals(verdict)) {
            return "可靠";
        }
        if ("TIGHT".equals(verdict)) {
            return "偏紧";
        }
        if ("INSUFFICIENT".equals(verdict)) {
            return "不足";
        }
        return verdict == null ? "-" : verdict;
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
        delta.put("capitalUtilization", difference(scenario, baseline, "capitalUtilization"));
        delta.put("cashUsed", difference(scenario, baseline, "cashUsed"));
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

    private int intVal(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
