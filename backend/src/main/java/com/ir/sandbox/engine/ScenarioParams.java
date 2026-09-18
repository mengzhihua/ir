package com.ir.sandbox.engine;

import lombok.Data;
import com.ir.common.CarrierCodes;
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
    private Map<String, BigDecimal> carrierLead = new LinkedHashMap<>();
    private int safetyDays = 3;
    private BigDecimal storageCostPerUnitDay = BigDecimal.valueOf(0.02);
    private BigDecimal handlingCostPerOrder = BigDecimal.valueOf(1.5);
    private BigDecimal packagingCostPerOrder = BigDecimal.valueOf(0.8);
    private BigDecimal stockoutPenaltyPerUnit = BigDecimal.valueOf(20);
    private int replenishLeadDays = 3;
    private BigDecimal initialInventoryMultiplier = BigDecimal.ONE;
    private BigDecimal costWeight = BigDecimal.valueOf(0.5);
    private BigDecimal efficiencyWeight = BigDecimal.valueOf(0.5);
    private BigDecimal workingCapital = new BigDecimal("100000000");
    private BigDecimal purchaseCostPerUnit = BigDecimal.valueOf(50);

    public ScenarioParams() {
        channelDemandMultiplier.put("TMALL", BigDecimal.ONE);
        channelDemandMultiplier.put("JD", BigDecimal.ONE);
        channelDemandMultiplier.put("DOUYIN", BigDecimal.ONE);
        channelDemandMultiplier.put("OFFLINE", BigDecimal.ONE);
        channelDemandMultiplier.put("API", BigDecimal.ONE);
        carrierMix.put(CarrierCodes.SF, BigDecimal.valueOf(0.4));
        carrierMix.put(CarrierCodes.JD, BigDecimal.valueOf(0.3));
        carrierMix.put(CarrierCodes.SELF01, BigDecimal.valueOf(0.3));
        carrierRate.put(CarrierCodes.SF, CarrierCodes.rate(CarrierCodes.SF));
        carrierRate.put(CarrierCodes.JD, CarrierCodes.rate(CarrierCodes.JD));
        carrierRate.put(CarrierCodes.SELF01, CarrierCodes.rate(CarrierCodes.SELF01));
        carrierLead.put(CarrierCodes.SF, CarrierCodes.lead(CarrierCodes.SF));
        carrierLead.put(CarrierCodes.JD, CarrierCodes.lead(CarrierCodes.JD));
        carrierLead.put(CarrierCodes.SELF01, CarrierCodes.lead(CarrierCodes.SELF01));
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
        normalized.setCostWeight(costWeight == null
                ? BigDecimal.valueOf(0.5) : costWeight);
        normalized.setEfficiencyWeight(efficiencyWeight == null
                ? BigDecimal.valueOf(0.5) : efficiencyWeight);
        normalized.setWorkingCapital(workingCapital == null
                || workingCapital.signum() < 0
                ? new BigDecimal("100000000") : workingCapital);
        normalized.setPurchaseCostPerUnit(purchaseCostPerUnit == null
                || purchaseCostPerUnit.signum() < 0
                ? BigDecimal.valueOf(50) : purchaseCostPerUnit);
        if (channelDemandMultiplier != null
                && !channelDemandMultiplier.isEmpty()) {
            normalized.setChannelDemandMultiplier(
                    new LinkedHashMap<>(channelDemandMultiplier));
        }
        if (carrierMix != null && !carrierMix.isEmpty()) {
            normalized.setCarrierMix(CarrierCodes.mergeMix(carrierMix));
        }
        normalized.setCarrierRate(CarrierCodes.mergeRates(carrierRate));
        normalized.setCarrierLead(CarrierCodes.mergeLeads(carrierLead));
        return normalized;
    }
}
