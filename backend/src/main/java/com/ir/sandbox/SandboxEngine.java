package com.ir.sandbox;

import com.ir.forecast.ForecastEngine;
import com.ir.snapshot.InventorySnapshot;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SandboxEngine {
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
    }

    public Result run(ScenarioParams params, BaselineData baseline) {
        Result result = new Result();
        int days = Math.max(1, params.getHorizonDays());
        Map<String, BigDecimal> stock = initialStock(params, baseline);
        BigDecimal totalDemand = BigDecimal.ZERO;
        BigDecimal totalFulfilled = BigDecimal.ZERO;
        BigDecimal totalStockout = BigDecimal.ZERO;
        BigDecimal totalLead = BigDecimal.ZERO;
        int leadCount = 0;

        for (Map.Entry<String, List<BigDecimal>> entry
                : baseline.getDemandBySku().entrySet()) {
            String sku = entry.getKey();
            List<BigDecimal> forecastValues =
                    forecast.seasonalNaive(entry.getValue(), days);
            BigDecimal skuDemand = BigDecimal.ZERO;
            BigDecimal skuFulfilled = BigDecimal.ZERO;

            for (int day = 0; day < days; day++) {
                BigDecimal demand = forecastValues.get(day)
                        .multiply(params.getDemandMultiplier());
                demand = demand.max(BigDecimal.ZERO);
                String warehouse = chooseWarehouse(
                        params, baseline, sku, demand, stock);
                BigDecimal available = stock.getOrDefault(
                        warehouse + "/" + sku, BigDecimal.ZERO);
                BigDecimal fulfilled = available.min(demand).max(BigDecimal.ZERO);
                BigDecimal stockout = demand.subtract(fulfilled).max(BigDecimal.ZERO);
                stock.put(warehouse + "/" + sku, available.subtract(fulfilled));

                if (day == params.getReplenishLeadDays()) {
                    BigDecimal replenishment = dailySafety(
                            forecastValues, params.getSafetyDays());
                    stock.put(warehouse + "/" + sku,
                            stock.get(warehouse + "/" + sku).add(replenishment));
                }

                BigDecimal freight = freightCost(params, demand, warehouse);
                BigDecimal handling = params.getHandlingCostPerOrder()
                        .multiply(fulfilled.signum() > 0
                                ? BigDecimal.ONE : BigDecimal.ZERO);
                BigDecimal packaging = params.getPackagingCostPerOrder()
                        .multiply(fulfilled.signum() > 0
                                ? BigDecimal.ONE : BigDecimal.ZERO);
                BigDecimal penalty = stockout.multiply(
                        params.getStockoutPenaltyPerUnit());
                BigDecimal dailyCost = freight.add(handling)
                        .add(packaging).add(penalty);
                add(result.getCostByType(), "FREIGHT", freight);
                add(result.getCostByType(), "HANDLING", handling);
                add(result.getCostByType(), "PACKAGING", packaging);
                add(result.getCostByType(), "STOCKOUT_PENALTY", penalty);
                add(result.getCostByWarehouse(), warehouse, dailyCost);
                result.getDailySeries().add(day(day, demand, fulfilled, dailyCost));

                if (fulfilled.signum() > 0) {
                    totalLead = totalLead.add(leadDays(warehouse));
                    leadCount++;
                }
                skuDemand = skuDemand.add(demand);
                skuFulfilled = skuFulfilled.add(fulfilled);
                totalDemand = totalDemand.add(demand);
                totalFulfilled = totalFulfilled.add(fulfilled);
                totalStockout = totalStockout.add(stockout);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sku", sku);
            summary.put("demand", skuDemand);
            summary.put("fulfilled", skuFulfilled);
            summary.put("stockout", skuDemand.subtract(skuFulfilled));
            result.getPerSkuSummary().add(summary);
        }

        BigDecimal storage = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : stock.entrySet()) {
            BigDecimal amount = entry.getValue()
                    .multiply(params.getStorageCostPerUnitDay())
                    .multiply(BigDecimal.valueOf(days));
            storage = storage.add(amount);
            String warehouse = entry.getKey().split("/", 2)[0];
            add(result.getCostByWarehouse(), warehouse, amount);
        }
        add(result.getCostByType(), "STORAGE", storage);
        result.setStockoutUnits(totalStockout);
        result.setTotalCost(sum(result.getCostByType()));
        result.setServiceLevel(totalDemand.signum() == 0
                ? BigDecimal.ONE
                : totalFulfilled.divide(totalDemand, 6, RoundingMode.HALF_UP));
        result.setAvgLeadDays(leadCount == 0
                ? BigDecimal.ZERO
                : totalLead.divide(BigDecimal.valueOf(leadCount), 6,
                RoundingMode.HALF_UP));
        return result;
    }

    private Map<String, BigDecimal> initialStock(
            ScenarioParams params,
            BaselineData baseline) {
        Map<String, BigDecimal> stock = new LinkedHashMap<>();
        for (InventorySnapshot item : baseline.getInventory()) {
            stock.put(item.getWarehouseCode() + "/" + item.getSku(),
                    item.getQtyAvailable().multiply(
                            params.getInitialInventoryMultiplier()));
        }
        return stock;
    }

    private String chooseWarehouse(
            ScenarioParams params,
            BaselineData baseline,
            String sku,
            BigDecimal demand,
            Map<String, BigDecimal> stock) {
        if ("SINGLE_WAREHOUSE".equals(params.getAllocationStrategy())) {
            return params.getSingleWarehouse() == null
                    ? "WH-SH" : params.getSingleWarehouse();
        }

        List<String> warehouses = Arrays.asList("WH-SH", "WH-BJ", "WH-GZ");
        if ("LOWEST_COST".equals(params.getAllocationStrategy())) {
            String best = warehouses.get(0);
            BigDecimal bestCost = null;
            for (String warehouse : warehouses) {
                BigDecimal available = stock.getOrDefault(
                        warehouse + "/" + sku, BigDecimal.ZERO);
                if (available.signum() <= 0) {
                    continue;
                }
                BigDecimal cost = averageRate(params)
                        .multiply(distance(warehouse))
                        .add(params.getHandlingCostPerOrder());
                if (bestCost == null || cost.compareTo(bestCost) < 0) {
                    best = warehouse;
                    bestCost = cost;
                }
            }
            return best;
        }

        if ("BALANCED".equals(params.getAllocationStrategy())) {
            String best = warehouses.get(0);
            BigDecimal bestRatio = BigDecimal.valueOf(-1);
            for (String warehouse : warehouses) {
                BigDecimal available = stock.getOrDefault(
                        warehouse + "/" + sku, BigDecimal.ZERO);
                BigDecimal ratio = demand.signum() == 0
                        ? available : available.divide(demand, 6,
                        RoundingMode.HALF_UP);
                if (ratio.compareTo(bestRatio) > 0) {
                    best = warehouse;
                    bestRatio = ratio;
                }
            }
            return best;
        }

        return baseline.getSkuWarehouse().getOrDefault(sku, "WH-SH");
    }

    private BigDecimal freightCost(
            ScenarioParams params,
            BigDecimal quantity,
            String warehouse) {
        BigDecimal averageRate = averageRate(params);
        BigDecimal averageWeight = BigDecimal.valueOf(1.5);
        return quantity.multiply(averageWeight)
                .multiply(averageRate)
                .multiply(distance(warehouse));
    }

    private BigDecimal averageRate(ScenarioParams params) {
        BigDecimal rate = BigDecimal.ZERO;
        BigDecimal ratio = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry
                : params.getCarrierMix().entrySet()) {
            BigDecimal weight = entry.getValue();
            rate = rate.add(params.getCarrierRate().getOrDefault(
                    entry.getKey(), BigDecimal.ZERO).multiply(weight));
            ratio = ratio.add(weight);
            add(new LinkedHashMap<>(), entry.getKey(), BigDecimal.ZERO);
        }
        return ratio.signum() == 0 ? BigDecimal.ONE
                : rate.divide(ratio, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal dailySafety(List<BigDecimal> values, int safetyDays) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total.divide(BigDecimal.valueOf(values.size()), 6,
                RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(safetyDays));
    }

    private BigDecimal distance(String warehouse) {
        if ("WH-BJ".equals(warehouse)) {
            return BigDecimal.valueOf(1.8);
        }
        if ("WH-GZ".equals(warehouse)) {
            return BigDecimal.valueOf(2.2);
        }
        return BigDecimal.valueOf(1.0);
    }

    private BigDecimal leadDays(String warehouse) {
        return distance(warehouse).multiply(BigDecimal.valueOf(1.2));
    }

    private Map<String, Object> day(
            int offset,
            BigDecimal demand,
            BigDecimal fulfilled,
            BigDecimal cost) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", LocalDate.now().plusDays(offset + 1L));
        row.put("demand", demand);
        row.put("fulfilled", fulfilled);
        row.put("cost", cost);
        return row;
    }

    private BigDecimal sum(Map<String, BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values.values()) {
            result = result.add(value);
        }
        return result;
    }

    private void add(Map<String, BigDecimal> values, String key, BigDecimal value) {
        values.put(key, values.getOrDefault(key, BigDecimal.ZERO).add(value));
    }
}
