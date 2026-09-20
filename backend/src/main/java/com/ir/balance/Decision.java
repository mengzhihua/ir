package com.ir.balance;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Decision {
    private String strategy;
    private String objectiveCode;
    private String actionType;
    private String targetKey;
    private Map<String, Object> params = new LinkedHashMap<>();
    private BigDecimal expectedCostDelta = BigDecimal.ZERO;
    private BigDecimal expectedNpsDelta = BigDecimal.ZERO;
    private String riskLevel = "LOW";
    private boolean approvalRequired;
    private String reason;
    private double priority;

    public Decision param(String key, Object value) {
        params.put(key, value);
        return this;
    }
}
