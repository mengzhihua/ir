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
    }

    public Result run(ScenarioParams params, BaselineData baseline) {
        Result result = new Result();
        int days = Math.max(1, params.getHorizonDays());
        Map<String, BigDecimal> stock = initialStock(params, baseline);
        Map<Integer, Map<String, BigDecimal>> arrivals = new LinkedHashMap<>();
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
            Map<String, BigDecimal> channelShare = baseline.getChannelShare()
                    .getOrDefault(sku, Collections.singletonMap("ALL",
                            BigDecimal.ONE));
            Map<String, BigDecimal> regionShare = baseline.getRegionShare()
                    .getOrDefault(sku, baseline.getRegionShare().getOrDefault(
                            "*", Collections.singletonMap("华东",
                                    BigDecimal.ONE)));
            BigDecimal skuDemand = BigDecimal.ZERO;
            BigDecimal skuFulfilled = BigDecimal.ZERO;
            BigDecimal skuStockout = BigDecimal.ZERO;

            for (int day = 0; day < days; day++) {
                receive(arrivals, day, stock);
                BigDecimal averageDemand = average(forecastValues)
                        .multiply(params.getDemandMultiplier())
                        .max(BigDecimal.ZERO);
                BigDecimal demand = channelDemand(params, channelShare,
                        forecastValues.get(day))
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
                        : regionShare.entrySet()) {
                    BigDecimal regionalDemand = demand.multiply(region.getValue());
                    String warehouse = chooseWarehouse(params, sku,
                            region.getKey(), regionalDemand, stock);
                    BigDecimal available = stock.getOrDefault(
                            key(warehouse, sku), BigDecimal.ZERO);
                    BigDecimal regionalFulfilled = available.min(
                            regionalDemand).max(BigDecimal.ZERO);
                    BigDecimal regionalStockout = regionalDemand.subtract(
                            regionalFulfilled).max(BigDecimal.ZERO);
                    stock.put(key(warehouse, sku),
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
                                BigDecimal.valueOf(1.2)));
                        dayLeadCount++;
                    }
                }

                scheduleReplenishment(params, sku, averageDemand, day,
                        stock, arrivals);
                BigDecimal penalty = stockout.multiply(
                        params.getStockoutPenaltyPerUnit());
                BigDecimal storage = storageCost(params, sku, stock);
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
                result.getDailySeries().add(day(day, demand, fulfilled,
                        dailyCost));

                if (dayLeadCount > 0) {
                    totalLead = totalLead.add(dayLead.divide(
                            BigDecimal.valueOf(dayLeadCount), 6,
                            RoundingMode.HALF_UP));
                    leadCount++;
                }
                skuDemand = skuDemand.add(demand);
                skuFulfilled = skuFulfilled.add(fulfilled);
                skuStockout = skuStockout.add(stockout);
                totalDemand = totalDemand.add(demand);
                totalFulfilled = totalFulfilled.add(fulfilled);
                totalStockout = totalStockout.add(stockout);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sku", sku);
            summary.put("demand", skuDemand);
            summary.put("fulfilled", skuFulfilled);
            summary.put("stockout", skuStockout);
            result.getPerSkuSummary().add(summary);
        }

        result.setStockoutUnits(totalStockout);
        result.setTotalCost(sum(result.getCostByType()));
        result.setServiceLevel(totalDemand.signum() == 0
                ? BigDecimal.ONE
                : totalFulfilled.divide(totalDemand, 6,
                        RoundingMode.HALF_UP));
        result.setAvgLeadDays(leadCount == 0
                ? BigDecimal.ZERO
                : totalLead.divide(BigDecimal.valueOf(leadCount), 6,
                        RoundingMode.HALF_UP));
        return result;
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
            Map<Integer, Map<String, BigDecimal>> arrivals) {
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
                value = demand.signum() == 0 ? available
                        : available.divide(demand, 6,
                        RoundingMode.HALF_UP).negate();
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
            stock.put(key(item.getWarehouseCode(), item.getSku()),
                    item.getQtyAvailable().multiply(
                            params.getInitialInventoryMultiplier()));
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
        row.put("demand", demand);
        row.put("fulfilled", fulfilled);
        row.put("cost", cost);
        return row;
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
