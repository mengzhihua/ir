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
    }

    public Result run(ScenarioParams params, BaselineData baseline) {
        params = params == null ? new ScenarioParams() : params.normalized();
        Result result = new Result();
        int days = Math.max(1, params.getHorizonDays());
        Map<String, BigDecimal> stock = initialStock(params, baseline);
        Map<Integer, Map<String, BigDecimal>> arrivals = new LinkedHashMap<>();
        Cash cash = new Cash(params.getWorkingCapital());
        BigDecimal totalDemand = BigDecimal.ZERO;
        BigDecimal totalFulfilled = BigDecimal.ZERO;
        BigDecimal totalStockout = BigDecimal.ZERO;
        BigDecimal totalLead = BigDecimal.ZERO;
        int leadCount = 0;

        List<SkuPlan> plans = new ArrayList<>();
        if (baseline != null && baseline.getDemandBySku() != null) {
            for (Map.Entry<String, List<BigDecimal>> entry
                    : baseline.getDemandBySku().entrySet()) {
                SkuPlan plan = new SkuPlan();
                plan.sku = entry.getKey();
                plan.forecast = forecast.seasonalNaive(entry.getValue(), days);
                plan.channelShare = baseline.getChannelShare()
                        .getOrDefault(plan.sku, Collections.singletonMap("ALL",
                                BigDecimal.ONE));
                plan.regionShare = baseline.getRegionShare()
                        .getOrDefault(plan.sku, baseline.getRegionShare().getOrDefault(
                                "*", Collections.singletonMap("华东",
                                        BigDecimal.ONE)));
                plans.add(plan);
            }
        }

        BigDecimal[] dayDemand = new BigDecimal[days];
        BigDecimal[] dayFulfilled = new BigDecimal[days];
        BigDecimal[] dayCost = new BigDecimal[days];
        Arrays.fill(dayDemand, BigDecimal.ZERO);
        Arrays.fill(dayFulfilled, BigDecimal.ZERO);
        Arrays.fill(dayCost, BigDecimal.ZERO);

        for (int day = 0; day < days; day++) {
            receive(arrivals, day, stock);
            for (SkuPlan plan : plans) {
                BigDecimal averageDemand = average(plan.forecast)
                        .multiply(params.getDemandMultiplier())
                        .max(BigDecimal.ZERO);
                BigDecimal demand = channelDemand(params, plan.channelShare,
                        plan.forecast.get(day))
                        .multiply(params.getDemandMultiplier())
                        .max(BigDecimal.ZERO);
                BigDecimal fulfilled = BigDecimal.ZERO;
                BigDecimal stockout = BigDecimal.ZERO;
                BigDecimal dayFreight = BigDecimal.ZERO;
                BigDecimal dayHandling = BigDecimal.ZERO;
                BigDecimal dayPackaging = BigDecimal.ZERO;
                BigDecimal dayLead = BigDecimal.ZERO;
                int dayLeadCount = 0;
                Map<String, BigDecimal> dayWarehouse =
                        new LinkedHashMap<>();

                for (Map.Entry<String, BigDecimal> region
                        : plan.regionShare.entrySet()) {
                    BigDecimal regionalDemand = demand.multiply(region.getValue());
                    String warehouse = chooseWarehouse(params, plan.sku,
                            region.getKey(), regionalDemand, stock);
                    BigDecimal available = stock.getOrDefault(
                            key(warehouse, plan.sku), BigDecimal.ZERO);
                    BigDecimal regionalFulfilled = available.min(
                            regionalDemand).max(BigDecimal.ZERO);
                    BigDecimal regionalStockout = regionalDemand.subtract(
                            regionalFulfilled).max(BigDecimal.ZERO);
                    stock.put(key(warehouse, plan.sku),
                            available.subtract(regionalFulfilled));
                    fulfilled = fulfilled.add(regionalFulfilled);
                    stockout = stockout.add(regionalStockout);

                    BigDecimal distance = distance(warehouse, region.getKey());
                    dayFreight = dayFreight.add(carrierFreight(
                            params, regionalFulfilled, distance, result));
                    add(dayWarehouse, warehouse,
                            carrierFreightValue(params, regionalFulfilled,
                                    distance));
                    if (regionalFulfilled.signum() > 0) {
                        dayHandling = dayHandling.add(
                                params.getHandlingCostPerOrder());
                        dayPackaging = dayPackaging.add(
                                params.getPackagingCostPerOrder());
                        add(dayWarehouse, warehouse,
                                params.getHandlingCostPerOrder().add(
                                        params.getPackagingCostPerOrder()));
                        dayLead = dayLead.add(distance.multiply(
                                BigDecimal.valueOf(1.2)).multiply(
                                carrierLeadFactor(params)));
                        dayLeadCount++;
                    }
                }

                spendOps(cash, dayFreight.add(dayHandling).add(dayPackaging));
                scheduleReplenishment(params, plan.sku, averageDemand, day,
                        stock, arrivals, cash);
                BigDecimal penalty = stockout.multiply(
                        params.getStockoutPenaltyPerUnit());
                BigDecimal storage = storageCost(params, plan.sku, stock);
                BigDecimal dailyCost = dayFreight.add(dayHandling)
                        .add(dayPackaging).add(storage).add(penalty);
                add(result.getCostByType(), "FREIGHT", dayFreight);
                add(result.getCostByType(), "HANDLING", dayHandling);
                add(result.getCostByType(), "PACKAGING", dayPackaging);
                add(result.getCostByType(), "STORAGE", storage);
                add(result.getCostByType(), "STOCKOUT_PENALTY", penalty);
                for (Map.Entry<String, BigDecimal> row
                        : dayWarehouse.entrySet()) {
                    add(result.getCostByWarehouse(), row.getKey(),
                            row.getValue());
                }
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
        result.setInventoryUnits(round(sum(stockAfterInit(params, baseline)), 2));
        BigDecimal unit = params.getPurchaseCostPerUnit() == null
                ? BigDecimal.valueOf(50) : params.getPurchaseCostPerUnit();
        result.setInventoryValue(round(result.getInventoryUnits().multiply(unit), 2));
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
        return result;
    }

    private Map<String, BigDecimal> stockAfterInit(
            ScenarioParams params, BaselineData baseline) {
        return initialStock(params, baseline);
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
        private BigDecimal demand = BigDecimal.ZERO;
        private BigDecimal fulfilled = BigDecimal.ZERO;
        private BigDecimal stockout = BigDecimal.ZERO;
    }

    private BigDecimal channelDemand(
            ScenarioParams params,
            Map<String, BigDecimal> shares,
            BigDecimal base) {
        BigDecimal result = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : shares.entrySet()) {
            BigDecimal multiplier = params.getChannelDemandMultiplier()
                    .getOrDefault(entry.getKey(), BigDecimal.ONE);
            result = result.add(base.multiply(entry.getValue())
                    .multiply(multiplier));
        }
        return result;
    }

    private void scheduleReplenishment(
            ScenarioParams params,
            String sku,
            BigDecimal averageDemand,
            int day,
            Map<String, BigDecimal> stock,
            Map<Integer, Map<String, BigDecimal>> arrivals,
            Cash cash) {
        if (averageDemand.signum() <= 0) {
            return;
        }
        for (String warehouse : WAREHOUSES) {
            BigDecimal available = stock.getOrDefault(key(warehouse, sku),
                    BigDecimal.ZERO);
            BigDecimal outstanding = outstanding(arrivals, day,
                    key(warehouse, sku));
            BigDecimal coverage = available.divide(averageDemand, 6,
                    RoundingMode.HALF_UP);
            if (coverage.compareTo(BigDecimal.valueOf(
                    params.getSafetyDays())) < 0) {
                BigDecimal target = averageDemand.multiply(
                        BigDecimal.valueOf(params.getSafetyDays()));
                BigDecimal quantity = target.subtract(available)
                        .subtract(outstanding).max(BigDecimal.ZERO);
                quantity = affordPurchase(params, cash, quantity);
                if (quantity.signum() > 0) {
                    int arrivalDay = day + Math.max(0,
                            params.getReplenishLeadDays());
                    arrivals.computeIfAbsent(arrivalDay,
                            ignored -> new LinkedHashMap<>());
                    Map<String, BigDecimal> due = arrivals.get(arrivalDay);
                    due.put(key(warehouse, sku),
                            due.getOrDefault(key(warehouse, sku),
                                    BigDecimal.ZERO).add(quantity));
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

    private BigDecimal outstanding(
            Map<Integer, Map<String, BigDecimal>> arrivals,
            int day,
            String key) {
        BigDecimal value = BigDecimal.ZERO;
        for (Map.Entry<Integer, Map<String, BigDecimal>> entry
                : arrivals.entrySet()) {
            if (entry.getKey() > day) {
                value = value.add(entry.getValue().getOrDefault(key,
                        BigDecimal.ZERO));
            }
        }
        return value;
    }

    private void receive(
            Map<Integer, Map<String, BigDecimal>> arrivals,
            int day,
            Map<String, BigDecimal> stock) {
        Map<String, BigDecimal> due = arrivals.remove(day);
        if (due == null) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : due.entrySet()) {
            stock.put(entry.getKey(), stock.getOrDefault(entry.getKey(),
                    BigDecimal.ZERO).add(entry.getValue()));
        }
    }

    private BigDecimal storageCost(
            ScenarioParams params,
            String sku,
            Map<String, BigDecimal> stock) {
        BigDecimal result = BigDecimal.ZERO;
        for (String warehouse : WAREHOUSES) {
            result = result.add(stock.getOrDefault(key(warehouse, sku),
                    BigDecimal.ZERO).multiply(
                    params.getStorageCostPerUnitDay()));
        }
        return result;
    }

    private String chooseWarehouse(
            ScenarioParams params,
            String sku,
            String region,
            BigDecimal demand,
            Map<String, BigDecimal> stock) {
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())) {
            return params.getSingleWarehouse() == null
                    ? "WH-SH" : params.getSingleWarehouse();
        }
        String best = "WH-SH";
        BigDecimal bestValue = null;
        for (String warehouse : WAREHOUSES) {
            BigDecimal available = stock.getOrDefault(key(warehouse, sku),
                    BigDecimal.ZERO);
            if (available.signum() <= 0
                    && !"BALANCED".equals(params.getAllocationStrategy())) {
                continue;
            }
            BigDecimal value;
            if ("LOWEST_COST".equals(params.getAllocationStrategy())) {
                value = effectiveRate(params).multiply(
                        distance(warehouse, region)).add(
                        params.getHandlingCostPerOrder());
            } else if ("BALANCED".equals(params.getAllocationStrategy())) {
                BigDecimal costPart = effectiveRate(params).multiply(
                        distance(warehouse, region)).add(
                        params.getHandlingCostPerOrder());
                BigDecimal leadPart = distance(warehouse, region)
                        .multiply(carrierLeadFactor(params));
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
            ScenarioParams params,
            BigDecimal quantity,
            BigDecimal distance,
            Result result) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (BigDecimal weight : params.getCarrierMix().values()) {
            weightTotal = weightTotal.add(weight);
        }
        if (weightTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        for (Map.Entry<String, BigDecimal> carrier
                : params.getCarrierMix().entrySet()) {
            BigDecimal portion = carrier.getValue().divide(weightTotal, 8,
                    RoundingMode.HALF_UP);
            BigDecimal amount = quantity.multiply(BigDecimal.valueOf(1.5))
                    .multiply(distance)
                    .multiply(params.getCarrierRate().getOrDefault(
                            carrier.getKey(), BigDecimal.ONE))
                    .multiply(portion);
            add(result.getCostByCarrier(), carrier.getKey(), amount);
            total = total.add(amount);
        }
        return total;
    }

    private BigDecimal carrierFreightValue(
            ScenarioParams params,
            BigDecimal quantity,
            BigDecimal distance) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (BigDecimal weight : params.getCarrierMix().values()) {
            weightTotal = weightTotal.add(weight);
        }
        if (weightTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        for (Map.Entry<String, BigDecimal> carrier
                : params.getCarrierMix().entrySet()) {
            total = total.add(quantity.multiply(BigDecimal.valueOf(1.5))
                    .multiply(distance)
                    .multiply(params.getCarrierRate().getOrDefault(
                            carrier.getKey(), BigDecimal.ONE))
                    .multiply(carrier.getValue().divide(weightTotal, 8,
                            RoundingMode.HALF_UP)));
        }
        return total;
    }

    private BigDecimal carrierLeadFactor(ScenarioParams params) {
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

    private BigDecimal effectiveRate(ScenarioParams params) {
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

    private Map<String, BigDecimal> initialStock(
            ScenarioParams params,
            BaselineData baseline) {
        Map<String, BigDecimal> stock = new LinkedHashMap<>();
        for (InventorySnapshot item : baseline.getInventory()) {
            String warehouse = com.ir.common.WarehouseCodes.toOms(item.getWarehouseCode());
            String stockKey = key(warehouse, item.getSku());
            BigDecimal qty = item.getQtyAvailable() == null
                    ? BigDecimal.ZERO
                    : item.getQtyAvailable().multiply(params.getInitialInventoryMultiplier());
            stock.put(stockKey, stock.getOrDefault(stockKey, BigDecimal.ZERO).add(qty));
        }
        return stock;
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
