package com.ir.sandbox.engine;

import lombok.Data;
import org.springframework.stereotype.Component;
import com.ir.forecast.engine.ForecastEngine;
import com.ir.snapshot.entity.InventorySnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SandboxEngine {
    private static final List<String> WAREHOUSES =
            Arrays.asList("WH-SH", "WH-BJ", "WH-GZ");

    private final ForecastEngine forecast;

    public SandboxEngine() {
        this(new ForecastEngine());
    }

    public SandboxEngine(ForecastEngine forecast) {
        this.forecast = forecast;
    }

    @Data
    public static class Result {
        private BigDecimal totalCost = BigDecimal.ZERO;
        private Map<String, BigDecimal> costByType = new LinkedHashMap<>();
        private Map<String, BigDecimal> costByWarehouse = new LinkedHashMap<>();
        private Map<String, BigDecimal> costByCarrier = new LinkedHashMap<>();
        private BigDecimal serviceLevel = BigDecimal.ZERO;
        private BigDecimal stockoutUnits = BigDecimal.ZERO;
        private BigDecimal avgLeadDays = BigDecimal.ZERO;
        private List<Map<String, Object>> dailySeries = new ArrayList<>();
        private List<Map<String, Object>> perSkuSummary = new ArrayList<>();
        private BigDecimal workingCapital = BigDecimal.ZERO;
        private BigDecimal cashUsed = BigDecimal.ZERO;
        private BigDecimal cashRemaining = BigDecimal.ZERO;
        private BigDecimal purchaseCash = BigDecimal.ZERO;
        private BigDecimal opsCash = BigDecimal.ZERO;
        private BigDecimal deferredPurchaseQty = BigDecimal.ZERO;
        private BigDecimal capitalShortage = BigDecimal.ZERO;
        private BigDecimal capitalUtilization = BigDecimal.ZERO;
        private Boolean capitalFeasible = Boolean.TRUE;
        private String capitalVerdict = "RELIABLE";
        private String capitalReason = "";
        private int skuCount;
        private BigDecimal inventoryUnits = BigDecimal.ZERO;
        private BigDecimal inventoryValue = BigDecimal.ZERO;
        private boolean skuSummaryTruncated;
        private long elapsedMs;
    }

    public Result run(ScenarioParams params, BaselineData baseline) {
        long started = System.currentTimeMillis();
        params = params == null ? new ScenarioParams() : params.normalized();
        Result result = new Result();
        int days = Math.max(1, params.getHorizonDays());
        Map<String, BigDecimal> stock = new HashMap<>();
        Map<String, List<String>> skuWarehouses = new HashMap<>();
        BigDecimal inventoryUnits = loadStock(params, baseline, stock, skuWarehouses);
        Map<Integer, Map<String, BigDecimal>> arrivals = new HashMap<>();
        Map<String, BigDecimal> outstanding = new HashMap<>();
        Cash cash = new Cash(params.getWorkingCapital());
        RunCache cache = RunCache.of(params);
        BigDecimal totalDemand = BigDecimal.ZERO;
        BigDecimal totalFulfilled = BigDecimal.ZERO;
        BigDecimal totalStockout = BigDecimal.ZERO;
        BigDecimal totalLead = BigDecimal.ZERO;
        int leadCount = 0;

        List<SkuPlan> plans = buildPlans(params, baseline, skuWarehouses, days, cache);

        BigDecimal[] dayDemand = new BigDecimal[days];
        BigDecimal[] dayFulfilled = new BigDecimal[days];
        BigDecimal[] dayCost = new BigDecimal[days];
        Arrays.fill(dayDemand, BigDecimal.ZERO);
        Arrays.fill(dayFulfilled, BigDecimal.ZERO);
        Arrays.fill(dayCost, BigDecimal.ZERO);

        for (int day = 0; day < days; day++) {
            receive(arrivals, day, stock, outstanding);
            for (SkuPlan plan : plans) {
                BigDecimal demand = plan.forecast.get(day)
                        .multiply(plan.channelFactor)
                        .multiply(cache.demandMultiplier)
                        .max(BigDecimal.ZERO);
                BigDecimal fulfilled = BigDecimal.ZERO;
                BigDecimal stockout = BigDecimal.ZERO;
                BigDecimal dayFreight = BigDecimal.ZERO;
                BigDecimal dayHandling = BigDecimal.ZERO;
                BigDecimal dayPackaging = BigDecimal.ZERO;
                BigDecimal dayLead = BigDecimal.ZERO;
                int dayLeadCount = 0;

                for (Map.Entry<String, BigDecimal> region
                        : plan.regionShare.entrySet()) {
                    BigDecimal regionalDemand = demand.multiply(region.getValue());
                    String warehouse = chooseWarehouse(params, plan, region.getKey(),
                            regionalDemand, stock, cache);
                    String stockKey = key(warehouse, plan.sku);
                    BigDecimal available = stock.getOrDefault(stockKey, BigDecimal.ZERO);
                    BigDecimal regionalFulfilled = available.min(
                            regionalDemand).max(BigDecimal.ZERO);
                    BigDecimal regionalStockout = regionalDemand.subtract(
                            regionalFulfilled).max(BigDecimal.ZERO);
                    if (regionalFulfilled.signum() != 0) {
                        stock.put(stockKey, available.subtract(regionalFulfilled));
                    }
                    fulfilled = fulfilled.add(regionalFulfilled);
                    stockout = stockout.add(regionalStockout);

                    if (regionalFulfilled.signum() > 0) {
                        BigDecimal distance = distance(warehouse, region.getKey());
                        BigDecimal freight = carrierFreight(
                                cache, regionalFulfilled, distance, result);
                        dayFreight = dayFreight.add(freight);
                        add(result.getCostByWarehouse(), warehouse,
                                freight.add(cache.handling).add(cache.packaging));
                        dayHandling = dayHandling.add(cache.handling);
                        dayPackaging = dayPackaging.add(cache.packaging);
                        dayLead = dayLead.add(distance.multiply(cache.leadTimes12));
                        dayLeadCount++;
                    }
                }

                spendOps(cash, dayFreight.add(dayHandling).add(dayPackaging));
                scheduleReplenishment(params, plan, day, stock, arrivals, outstanding, cash, cache);
                BigDecimal penalty = stockout.signum() == 0
                        ? BigDecimal.ZERO
                        : stockout.multiply(params.getStockoutPenaltyPerUnit());
                BigDecimal storage = storageCost(cache, plan, stock);
                BigDecimal dailyCost = dayFreight.add(dayHandling)
                        .add(dayPackaging).add(storage).add(penalty);
                add(result.getCostByType(), "FREIGHT", dayFreight);
                add(result.getCostByType(), "HANDLING", dayHandling);
                add(result.getCostByType(), "PACKAGING", dayPackaging);
                add(result.getCostByType(), "STORAGE", storage);
                add(result.getCostByType(), "STOCKOUT_PENALTY", penalty);
                dayDemand[day] = dayDemand[day].add(demand);
                dayFulfilled[day] = dayFulfilled[day].add(fulfilled);
                dayCost[day] = dayCost[day].add(dailyCost);

                if (dayLeadCount > 0) {
                    totalLead = totalLead.add(dayLead.divide(
                            BigDecimal.valueOf(dayLeadCount), 6,
                            RoundingMode.HALF_UP));
                    leadCount++;
                }
                plan.demand = plan.demand.add(demand);
                plan.fulfilled = plan.fulfilled.add(fulfilled);
                plan.stockout = plan.stockout.add(stockout);
                totalDemand = totalDemand.add(demand);
                totalFulfilled = totalFulfilled.add(fulfilled);
                totalStockout = totalStockout.add(stockout);
            }
        }

        for (int day = 0; day < days; day++) {
            result.getDailySeries().add(day(day, dayDemand[day],
                    dayFulfilled[day], dayCost[day]));
        }
        fillSkuSummary(result, plans);

        result.setSkuCount(plans.size());
        result.setInventoryUnits(round(inventoryUnits, 2));
        result.setInventoryValue(round(inventoryUnits.multiply(cache.purchaseUnit), 2));
        result.setStockoutUnits(round(totalStockout, 2));
        result.setTotalCost(round(sum(result.getCostByType()), 2));
        result.setServiceLevel(totalDemand.signum() == 0
                ? BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP)
                : round(totalFulfilled.divide(totalDemand, 6,
                        RoundingMode.HALF_UP), 4));
        result.setAvgLeadDays(leadCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : round(totalLead.divide(BigDecimal.valueOf(leadCount), 6,
                        RoundingMode.HALF_UP), 2));
        roundMap(result.getCostByType(), 2);
        roundMap(result.getCostByWarehouse(), 2);
        roundMap(result.getCostByCarrier(), 2);
        finishCapital(result, params, cash);
        result.setElapsedMs(System.currentTimeMillis() - started);
        return result;
    }

    private List<SkuPlan> buildPlans(
            ScenarioParams params,
            BaselineData baseline,
            Map<String, List<String>> skuWarehouses,
            int days,
            RunCache cache) {
        List<SkuPlan> plans = new ArrayList<>();
        if (baseline == null || baseline.getDemandBySku() == null) {
            return plans;
        }
        Map<String, BigDecimal> defaultRegion = baseline.getRegionShare().getOrDefault(
                "*", Collections.singletonMap("华东", BigDecimal.ONE));
        for (Map.Entry<String, List<BigDecimal>> entry
                : baseline.getDemandBySku().entrySet()) {
            SkuPlan plan = new SkuPlan();
            plan.sku = entry.getKey();
            plan.forecast = forecastOf(entry.getValue(), days);
            plan.channelShare = baseline.getChannelShare()
                    .getOrDefault(plan.sku, Collections.singletonMap("ALL",
                            BigDecimal.ONE));
            plan.regionShare = baseline.getRegionShare()
                    .getOrDefault(plan.sku, defaultRegion);
            plan.channelFactor = channelFactor(params, plan.channelShare);
            BigDecimal baseAvg = constant(plan.forecast)
                    ? plan.forecast.get(0)
                    : average(plan.forecast);
            plan.avgDemand = baseAvg.multiply(cache.demandMultiplier).max(BigDecimal.ZERO);
            List<String> warehouses = skuWarehouses.get(plan.sku);
            if (warehouses == null || warehouses.isEmpty()) {
                String home = baseline.getSkuWarehouse() == null
                        ? null : com.ir.common.WarehouseCodes.toOms(
                                baseline.getSkuWarehouse().get(plan.sku));
                plan.warehouses = Collections.singletonList(
                        home == null ? "WH-SH" : home);
            } else {
                plan.warehouses = warehouses;
            }
            plans.add(plan);
        }
        return plans;
    }

    private List<BigDecimal> forecastOf(List<BigDecimal> history, int days) {
        if (history == null || history.isEmpty()) {
            return forecast.seasonalNaive(Collections.<BigDecimal>emptyList(), days);
        }
        if (constant(history)) {
            if (history.size() >= days) {
                return history;
            }
            return Collections.nCopies(days, history.get(0));
        }
        return forecast.seasonalNaive(history, days);
    }

    private boolean constant(List<BigDecimal> history) {
        if (history.size() <= 1) {
            return true;
        }
        BigDecimal first = history.get(0);
        return first.compareTo(history.get(history.size() - 1)) == 0
                && first.compareTo(history.get(history.size() / 2)) == 0;
    }

    private BigDecimal channelFactor(
            ScenarioParams params,
            Map<String, BigDecimal> shares) {
        BigDecimal result = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : shares.entrySet()) {
            BigDecimal multiplier = params.getChannelDemandMultiplier()
                    .getOrDefault(entry.getKey(), BigDecimal.ONE);
            result = result.add(entry.getValue().multiply(multiplier));
        }
        return result;
    }

    private BigDecimal loadStock(
            ScenarioParams params,
            BaselineData baseline,
            Map<String, BigDecimal> stock,
            Map<String, List<String>> skuWarehouses) {
        BigDecimal units = BigDecimal.ZERO;
        if (baseline == null || baseline.getInventory() == null) {
            return units;
        }
        for (InventorySnapshot item : baseline.getInventory()) {
            String warehouse = com.ir.common.WarehouseCodes.toOms(item.getWarehouseCode());
            String sku = item.getSku();
            String stockKey = key(warehouse, sku);
            BigDecimal qty = item.getQtyAvailable() == null
                    ? BigDecimal.ZERO
                    : item.getQtyAvailable().multiply(params.getInitialInventoryMultiplier());
            stock.put(stockKey, stock.getOrDefault(stockKey, BigDecimal.ZERO).add(qty));
            units = units.add(qty);
            List<String> warehouses = skuWarehouses.get(sku);
            if (warehouses == null) {
                warehouses = new ArrayList<String>(2);
                skuWarehouses.put(sku, warehouses);
            }
            if (!warehouses.contains(warehouse)) {
                warehouses.add(warehouse);
            }
        }
        if (baseline.getSkuWarehouse() != null) {
            for (Map.Entry<String, String> entry : baseline.getSkuWarehouse().entrySet()) {
                String home = com.ir.common.WarehouseCodes.toOms(entry.getValue());
                if (home == null) {
                    continue;
                }
                List<String> warehouses = skuWarehouses.get(entry.getKey());
                if (warehouses == null) {
                    skuWarehouses.put(entry.getKey(), Collections.singletonList(home));
                } else if (!warehouses.contains(home)) {
                    warehouses.add(home);
                }
            }
        }
        return units;
    }

    private void fillSkuSummary(Result result, List<SkuPlan> plans) {
        List<SkuPlan> ranked = new ArrayList<SkuPlan>(plans);
        ranked.sort((left, right) -> right.stockout.compareTo(left.stockout));
        int limit = Math.min(CapitalTiers.MAX_SKU_SUMMARY, ranked.size());
        for (int i = 0; i < limit; i++) {
            SkuPlan plan = ranked.get(i);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sku", plan.sku);
            summary.put("demand", round(plan.demand, 2));
            summary.put("fulfilled", round(plan.fulfilled, 2));
            summary.put("stockout", round(plan.stockout, 2));
            result.getPerSkuSummary().add(summary);
        }
        result.setSkuSummaryTruncated(plans.size() > limit);
    }

    private static final class SkuPlan {
        private String sku;
        private List<BigDecimal> forecast;
        private Map<String, BigDecimal> channelShare;
        private Map<String, BigDecimal> regionShare;
        private List<String> warehouses;
        private BigDecimal channelFactor = BigDecimal.ONE;
        private BigDecimal avgDemand = BigDecimal.ZERO;
        private BigDecimal demand = BigDecimal.ZERO;
        private BigDecimal fulfilled = BigDecimal.ZERO;
        private BigDecimal stockout = BigDecimal.ZERO;
    }

    private static final class RunCache {
        private BigDecimal leadFactor;
        private BigDecimal leadTimes12;
        private BigDecimal effectiveRate;
        private BigDecimal handling;
        private BigDecimal packaging;
        private BigDecimal storageRate;
        private BigDecimal purchaseUnit;
        private BigDecimal demandMultiplier;
        private List<CarrierPart> carriers = new ArrayList<>();
        private String strategy;
        private String singleWarehouse;
        private int safetyDays;
        private int leadDays;

        private static RunCache of(ScenarioParams params) {
            RunCache cache = new RunCache();
            cache.leadFactor = leadFactor(params);
            cache.leadTimes12 = BigDecimal.valueOf(1.2).multiply(cache.leadFactor);
            cache.effectiveRate = rate(params);
            cache.handling = params.getHandlingCostPerOrder();
            cache.packaging = params.getPackagingCostPerOrder();
            cache.storageRate = params.getStorageCostPerUnitDay();
            cache.purchaseUnit = params.getPurchaseCostPerUnit() == null
                    ? BigDecimal.valueOf(50) : params.getPurchaseCostPerUnit();
            cache.demandMultiplier = params.getDemandMultiplier() == null
                    ? BigDecimal.ONE : params.getDemandMultiplier();
            cache.strategy = params.getAllocationStrategy();
            cache.singleWarehouse = params.getSingleWarehouse() == null
                    ? "WH-SH" : params.getSingleWarehouse();
            cache.safetyDays = params.getSafetyDays();
            cache.leadDays = Math.max(0, params.getReplenishLeadDays());
            BigDecimal weightTotal = BigDecimal.ZERO;
            for (BigDecimal weight : params.getCarrierMix().values()) {
                weightTotal = weightTotal.add(weight);
            }
            if (weightTotal.signum() > 0) {
                for (Map.Entry<String, BigDecimal> carrier
                        : params.getCarrierMix().entrySet()) {
                    CarrierPart part = new CarrierPart();
                    part.code = carrier.getKey();
                    BigDecimal portion = carrier.getValue().divide(weightTotal, 8,
                            RoundingMode.HALF_UP);
                    BigDecimal rate = params.getCarrierRate().getOrDefault(
                            carrier.getKey(), BigDecimal.ONE);
                    part.weight = portion.multiply(rate).multiply(BigDecimal.valueOf(1.5));
                    cache.carriers.add(part);
                }
            }
            return cache;
        }

        private static BigDecimal leadFactor(ScenarioParams params) {
            BigDecimal result = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            Map<String, BigDecimal> leads = params.getCarrierLead();
            for (Map.Entry<String, BigDecimal> entry
                    : params.getCarrierMix().entrySet()) {
                BigDecimal weight = entry.getValue() == null
                        ? BigDecimal.ZERO : entry.getValue();
                BigDecimal lead = leads == null
                        ? com.ir.common.CarrierCodes.lead(entry.getKey())
                        : leads.getOrDefault(entry.getKey(),
                        com.ir.common.CarrierCodes.lead(entry.getKey()));
                result = result.add(weight.multiply(lead));
                total = total.add(weight);
            }
            return total.signum() == 0 ? BigDecimal.ONE
                    : result.divide(total, 6, RoundingMode.HALF_UP);
        }

        private static BigDecimal rate(ScenarioParams params) {
            BigDecimal result = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry
                    : params.getCarrierMix().entrySet()) {
                BigDecimal weight = entry.getValue();
                result = result.add(weight.multiply(params.getCarrierRate()
                        .getOrDefault(entry.getKey(), BigDecimal.ONE)));
                total = total.add(weight);
            }
            return total.signum() == 0 ? BigDecimal.ONE
                    : result.divide(total, 6, RoundingMode.HALF_UP);
        }
    }

    private static final class CarrierPart {
        private String code;
        private BigDecimal weight;
    }

    private void scheduleReplenishment(
            ScenarioParams params,
            SkuPlan plan,
            int day,
            Map<String, BigDecimal> stock,
            Map<Integer, Map<String, BigDecimal>> arrivals,
            Map<String, BigDecimal> outstanding,
            Cash cash,
            RunCache cache) {
        if (plan.avgDemand.signum() <= 0) {
            return;
        }
        BigDecimal safety = BigDecimal.valueOf(cache.safetyDays);
        BigDecimal target = plan.avgDemand.multiply(safety);
        for (String warehouse : plan.warehouses) {
            String stockKey = key(warehouse, plan.sku);
            BigDecimal available = stock.getOrDefault(stockKey, BigDecimal.ZERO);
            BigDecimal inbound = outstanding.getOrDefault(stockKey, BigDecimal.ZERO);
            BigDecimal coverage = available.divide(plan.avgDemand, 6,
                    RoundingMode.HALF_UP);
            if (coverage.compareTo(safety) < 0) {
                BigDecimal quantity = target.subtract(available)
                        .subtract(inbound).max(BigDecimal.ZERO);
                quantity = affordPurchase(params, cash, quantity);
                if (quantity.signum() > 0) {
                    int arrivalDay = day + cache.leadDays;
                    Map<String, BigDecimal> due = arrivals.get(arrivalDay);
                    if (due == null) {
                        due = new HashMap<>();
                        arrivals.put(arrivalDay, due);
                    }
                    due.put(stockKey, due.getOrDefault(stockKey, BigDecimal.ZERO).add(quantity));
                    outstanding.put(stockKey, inbound.add(quantity));
                }
            }
        }
    }

    private BigDecimal affordPurchase(
            ScenarioParams params, Cash cash, BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal unit = params.getPurchaseCostPerUnit();
        if (unit == null || unit.signum() <= 0) {
            return quantity;
        }
        BigDecimal need = quantity.multiply(unit);
        if (need.compareTo(cash.remaining) <= 0) {
            cash.remaining = cash.remaining.subtract(need);
            cash.purchase = cash.purchase.add(need);
            return quantity;
        }
        BigDecimal affordable = cash.remaining.divide(unit, 6, RoundingMode.DOWN)
                .max(BigDecimal.ZERO);
        BigDecimal bought = affordable.min(quantity);
        BigDecimal spent = bought.multiply(unit);
        cash.deferredQty = cash.deferredQty.add(quantity.subtract(bought));
        cash.shortage = cash.shortage.add(need.subtract(spent));
        cash.purchase = cash.purchase.add(spent);
        cash.remaining = cash.remaining.subtract(spent);
        return bought;
    }

    private void spendOps(Cash cash, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        if (amount.compareTo(cash.remaining) <= 0) {
            cash.remaining = cash.remaining.subtract(amount);
            cash.ops = cash.ops.add(amount);
            return;
        }
        cash.shortage = cash.shortage.add(amount.subtract(cash.remaining));
        cash.ops = cash.ops.add(cash.remaining);
        cash.remaining = BigDecimal.ZERO;
    }

    private void finishCapital(Result result, ScenarioParams params, Cash cash) {
        BigDecimal capital = params.getWorkingCapital() == null
                ? BigDecimal.ZERO : params.getWorkingCapital();
        BigDecimal used = cash.purchase.add(cash.ops);
        result.setWorkingCapital(round(capital, 2));
        result.setPurchaseCash(round(cash.purchase, 2));
        result.setOpsCash(round(cash.ops, 2));
        result.setCashUsed(round(used, 2));
        result.setCashRemaining(round(cash.remaining, 2));
        result.setDeferredPurchaseQty(round(cash.deferredQty, 2));
        result.setCapitalShortage(round(cash.shortage, 2));
        BigDecimal utilization = capital.signum() == 0
                ? BigDecimal.ONE
                : used.divide(capital, 6, RoundingMode.HALF_UP);
        result.setCapitalUtilization(round(utilization, 4));
        boolean feasible = cash.shortage.signum() == 0
                && cash.deferredQty.signum() == 0
                && used.compareTo(capital) <= 0;
        result.setCapitalFeasible(feasible);
        if (!feasible) {
            result.setCapitalVerdict("INSUFFICIENT");
            result.setCapitalReason("资金盘覆盖不了采购或履约现金，补货被推迟或出现现金缺口");
        } else if (utilization.compareTo(new BigDecimal("0.80")) >= 0) {
            result.setCapitalVerdict("TIGHT");
            result.setCapitalReason("资金盘能撑住，但现金占用已超过 80%");
        } else {
            result.setCapitalVerdict("RELIABLE");
            result.setCapitalReason("资金盘覆盖履约与补货现金，占用低于 80%");
        }
    }

    private static final class Cash {
        private BigDecimal remaining;
        private BigDecimal purchase = BigDecimal.ZERO;
        private BigDecimal ops = BigDecimal.ZERO;
        private BigDecimal deferredQty = BigDecimal.ZERO;
        private BigDecimal shortage = BigDecimal.ZERO;

        private Cash(BigDecimal capital) {
            this.remaining = capital == null ? BigDecimal.ZERO : capital;
        }
    }

    private void receive(
            Map<Integer, Map<String, BigDecimal>> arrivals,
            int day,
            Map<String, BigDecimal> stock,
            Map<String, BigDecimal> outstanding) {
        Map<String, BigDecimal> due = arrivals.remove(day);
        if (due == null) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : due.entrySet()) {
            stock.put(entry.getKey(), stock.getOrDefault(entry.getKey(),
                    BigDecimal.ZERO).add(entry.getValue()));
            BigDecimal left = outstanding.get(entry.getKey());
            if (left != null) {
                left = left.subtract(entry.getValue());
                if (left.signum() <= 0) {
                    outstanding.remove(entry.getKey());
                } else {
                    outstanding.put(entry.getKey(), left);
                }
            }
        }
    }

    private BigDecimal storageCost(
            RunCache cache,
            SkuPlan plan,
            Map<String, BigDecimal> stock) {
        if (cache.storageRate.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal result = BigDecimal.ZERO;
        for (String warehouse : plan.warehouses) {
            result = result.add(stock.getOrDefault(key(warehouse, plan.sku),
                    BigDecimal.ZERO).multiply(cache.storageRate));
        }
        return result;
    }

    private String chooseWarehouse(
            ScenarioParams params,
            SkuPlan plan,
            String region,
            BigDecimal demand,
            Map<String, BigDecimal> stock,
            RunCache cache) {
        if ("SINGLE_WAREHOUSE".equals(cache.strategy)) {
            return cache.singleWarehouse;
        }
        if (!"BALANCED".equals(cache.strategy)
                && plan.warehouses.size() == 1) {
            return plan.warehouses.get(0);
        }
        String best = plan.warehouses.get(0);
        BigDecimal bestValue = null;
        for (String warehouse : WAREHOUSES) {
            BigDecimal available = stock.getOrDefault(key(warehouse, plan.sku),
                    BigDecimal.ZERO);
            if (available.signum() <= 0
                    && !"BALANCED".equals(cache.strategy)) {
                continue;
            }
            BigDecimal value;
            if ("LOWEST_COST".equals(cache.strategy)) {
                value = cache.effectiveRate.multiply(
                        distance(warehouse, region)).add(cache.handling);
            } else if ("BALANCED".equals(cache.strategy)) {
                BigDecimal costPart = cache.effectiveRate.multiply(
                        distance(warehouse, region)).add(cache.handling);
                BigDecimal leadPart = distance(warehouse, region)
                        .multiply(cache.leadFactor);
                BigDecimal costW = params.getCostWeight() == null
                        ? BigDecimal.valueOf(0.5) : params.getCostWeight();
                BigDecimal effW = params.getEfficiencyWeight() == null
                        ? BigDecimal.valueOf(0.5) : params.getEfficiencyWeight();
                value = costPart.multiply(costW).add(leadPart.multiply(effW));
                if (available.signum() <= 0) {
                    value = value.add(BigDecimal.TEN);
                }
                if (demand.signum() == 0) {
                    value = available.negate();
                }
            } else {
                value = distance(warehouse, region);
            }
            if (bestValue == null || value.compareTo(bestValue) < 0) {
                best = warehouse;
                bestValue = value;
            }
        }
        return best;
    }

    private BigDecimal carrierFreight(
            RunCache cache,
            BigDecimal quantity,
            BigDecimal distance,
            Result result) {
        if (quantity.signum() <= 0 || cache.carriers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CarrierPart part : cache.carriers) {
            BigDecimal amount = quantity.multiply(distance).multiply(part.weight);
            add(result.getCostByCarrier(), part.code, amount);
            total = total.add(amount);
        }
        return total;
    }

    private BigDecimal distance(String warehouse, String region) {
        if ("WH-BJ".equals(warehouse)) {
            return "华北".equals(region) ? BigDecimal.ONE
                    : "华东".equals(region) ? BigDecimal.valueOf(1.8)
                    : BigDecimal.valueOf(2.2);
        }
        if ("WH-GZ".equals(warehouse)) {
            return "华南".equals(region) ? BigDecimal.ONE
                    : "华东".equals(region) ? BigDecimal.valueOf(2.0)
                    : BigDecimal.valueOf(2.4);
        }
        return "华东".equals(region) ? BigDecimal.ONE
                : "华北".equals(region) ? BigDecimal.valueOf(1.8)
                : BigDecimal.valueOf(2.0);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total.divide(BigDecimal.valueOf(values.size()), 6,
                RoundingMode.HALF_UP);
    }

    private Map<String, Object> day(
            int offset,
            BigDecimal demand,
            BigDecimal fulfilled,
            BigDecimal cost) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", LocalDate.now().plusDays(offset + 1L));
        row.put("demand", round(demand, 2));
        row.put("fulfilled", round(fulfilled, 2));
        row.put("cost", round(cost, 2));
        return row;
    }

    private BigDecimal round(BigDecimal value, int scale) {
        return value == null
                ? BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
                : value.setScale(scale, RoundingMode.HALF_UP);
    }

    private void roundMap(Map<String, BigDecimal> values, int scale) {
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            entry.setValue(round(entry.getValue(), scale));
        }
    }

    private String key(String warehouse, String sku) {
        return warehouse + "/" + sku;
    }

    private BigDecimal sum(Map<String, BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values.values()) {
            result = result.add(value);
        }
        return result;
    }

    private void add(
            Map<String, BigDecimal> values,
            String key,
            BigDecimal value) {
        values.put(key, values.getOrDefault(key, BigDecimal.ZERO).add(value));
    }
}
