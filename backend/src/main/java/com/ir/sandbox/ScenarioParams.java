package com.ir.sandbox;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ScenarioParams {
    private int horizonDays = 30;
    private BigDecimal demandMultiplier = BigDecimal.ONE;
    private Map<String, BigDecimal> channelDemandMultiplier =
            new LinkedHashMap<>();
    private String allocationStrategy = "NEAREST";
    private String singleWarehouse;
    private Map<String, BigDecimal> carrierMix = new LinkedHashMap<>();
    private Map<String, BigDecimal> carrierRate = new LinkedHashMap<>();
    private int safetyDays = 3;
    private BigDecimal storageCostPerUnitDay = BigDecimal.valueOf(0.02);
    private BigDecimal handlingCostPerOrder = BigDecimal.valueOf(1.5);
    private BigDecimal packagingCostPerOrder = BigDecimal.valueOf(0.8);
    private BigDecimal stockoutPenaltyPerUnit = BigDecimal.valueOf(20);
    private int replenishLeadDays = 3;
    private BigDecimal initialInventoryMultiplier = BigDecimal.ONE;

    public ScenarioParams() {
        channelDemandMultiplier.put("TMALL", BigDecimal.ONE);
        channelDemandMultiplier.put("JD", BigDecimal.ONE);
        channelDemandMultiplier.put("DOUYIN", BigDecimal.ONE);
        channelDemandMultiplier.put("OFFLINE", BigDecimal.ONE);
        channelDemandMultiplier.put("API", BigDecimal.ONE);
        carrierMix.put("SF", BigDecimal.valueOf(0.4));
        carrierMix.put("JDL", BigDecimal.valueOf(0.3));
        carrierMix.put("SELF", BigDecimal.valueOf(0.3));
        carrierRate.put("SF", BigDecimal.valueOf(2.2));
        carrierRate.put("JDL", BigDecimal.valueOf(1.8));
        carrierRate.put("SELF", BigDecimal.valueOf(1.4));
    }

    public ScenarioParams normalized() {
        ScenarioParams normalized = new ScenarioParams();
        normalized.setHorizonDays(horizonDays);
        normalized.setDemandMultiplier(
                demandMultiplier == null ? BigDecimal.ONE : demandMultiplier);
        normalized.setAllocationStrategy(
                allocationStrategy == null ? "NEAREST" : allocationStrategy);
        normalized.setSingleWarehouse(singleWarehouse);
        normalized.setSafetyDays(safetyDays);
        normalized.setStorageCostPerUnitDay(
                storageCostPerUnitDay == null
                        ? BigDecimal.valueOf(0.02)
                        : storageCostPerUnitDay);
        normalized.setHandlingCostPerOrder(
                handlingCostPerOrder == null
                        ? BigDecimal.valueOf(1.5)
                        : handlingCostPerOrder);
        normalized.setPackagingCostPerOrder(
                packagingCostPerOrder == null
                        ? BigDecimal.valueOf(0.8)
                        : packagingCostPerOrder);
        normalized.setStockoutPenaltyPerUnit(
                stockoutPenaltyPerUnit == null
                        ? BigDecimal.valueOf(20)
                        : stockoutPenaltyPerUnit);
        normalized.setReplenishLeadDays(replenishLeadDays);
        normalized.setInitialInventoryMultiplier(
                initialInventoryMultiplier == null
                        ? BigDecimal.ONE
                        : initialInventoryMultiplier);
        if (channelDemandMultiplier != null
                && !channelDemandMultiplier.isEmpty()) {
            normalized.setChannelDemandMultiplier(
                    new LinkedHashMap<>(channelDemandMultiplier));
        }
        if (carrierMix != null && !carrierMix.isEmpty()) {
            normalized.setCarrierMix(new LinkedHashMap<>(carrierMix));
        }
        if (carrierRate != null && !carrierRate.isEmpty()) {
            normalized.setCarrierRate(new LinkedHashMap<>(carrierRate));
        }
        return normalized;
    }
}
