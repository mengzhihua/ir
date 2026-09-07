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
        carrierMix.put("SF", BigDecimal.valueOf(0.4));
        carrierMix.put("JDL", BigDecimal.valueOf(0.3));
        carrierMix.put("SELF", BigDecimal.valueOf(0.3));
        carrierRate.put("SF", BigDecimal.valueOf(2.2));
        carrierRate.put("JDL", BigDecimal.valueOf(1.8));
        carrierRate.put("SELF", BigDecimal.valueOf(1.4));
    }
}
