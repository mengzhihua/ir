package com.ir.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ir.system.entity.CtSetting;
import com.ir.system.mapper.CtSettingMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

/** 运行时成本/效率权重和补货安全/交期：启动读配置，沙盘推荐写入，立刻作用于预警和补货建议。 */

@Service
public class BalancePolicy {
    public static final String COST_KEY = "sandbox.cost-weight";
    public static final String EFF_KEY = "sandbox.efficiency-weight";
    public static final String SAFETY_KEY = "sandbox.safety-days";
    public static final String LEAD_KEY = "sandbox.replenish-lead-days";

    private final CtSettingMapper settings;
    private final BigDecimal defaultCost;
    private final BigDecimal defaultEfficiency;
    private final int defaultSafety;
    private final int defaultLead;
    private volatile BigDecimal costWeight;
    private volatile BigDecimal efficiencyWeight;
    private volatile int safetyDays;
    private volatile int replenishLeadDays;

    public BalancePolicy(
            CtSettingMapper settings,
            @Value("${ir.sandbox.cost-weight:0.5}") BigDecimal defaultCost,
            @Value("${ir.sandbox.efficiency-weight:0.5}") BigDecimal defaultEfficiency,
            @Value("${ir.sandbox.safety-days:3}") int defaultSafety,
            @Value("${ir.sandbox.replenish-lead-days:3}") int defaultLead) {
        this.settings = settings;
        this.defaultCost = defaultCost;
        this.defaultEfficiency = defaultEfficiency;
        this.defaultSafety = defaultSafety;
        this.defaultLead = defaultLead;
        this.costWeight = clamp(defaultCost);
        this.efficiencyWeight = clamp(defaultEfficiency);
        this.safetyDays = clampDays(defaultSafety);
        this.replenishLeadDays = clampDays(defaultLead);
    }

    @PostConstruct
    public void load() {
        try {
            this.costWeight = read(COST_KEY, defaultCost);
            this.efficiencyWeight = read(EFF_KEY, defaultEfficiency);
            this.safetyDays = readInt(SAFETY_KEY, defaultSafety);
            this.replenishLeadDays = readInt(LEAD_KEY, defaultLead);
        } catch (RuntimeException ex) {
            this.costWeight = clamp(defaultCost);
            this.efficiencyWeight = clamp(defaultEfficiency);
            this.safetyDays = clampDays(defaultSafety);
            this.replenishLeadDays = clampDays(defaultLead);
        }
    }

    public BigDecimal costWeight() {
        return costWeight;
    }

    public BigDecimal efficiencyWeight() {
        return efficiencyWeight;
    }

    public int safetyDays() {
        return safetyDays;
    }

    public int replenishLeadDays() {
        return replenishLeadDays;
    }

    public synchronized Map<String, Object> update(BigDecimal cost, BigDecimal efficiency) {
        return update(cost, efficiency, null, null);
    }

    public synchronized Map<String, Object> updateReplenish(Integer safety, Integer lead) {
        return update(costWeight, efficiencyWeight, safety, lead);
    }

    public synchronized Map<String, Object> update(
            BigDecimal cost, BigDecimal efficiency, Integer safety, Integer lead) {
        this.costWeight = clamp(cost);
        this.efficiencyWeight = clamp(efficiency);
        write(COST_KEY, this.costWeight);
        write(EFF_KEY, this.efficiencyWeight);
        if (safety != null) {
            this.safetyDays = clampDays(safety);
            write(SAFETY_KEY, this.safetyDays);
        }
        if (lead != null) {
            this.replenishLeadDays = clampDays(lead);
            write(LEAD_KEY, this.replenishLeadDays);
        }
        return snapshot();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("costWeight", costWeight);
        result.put("efficiencyWeight", efficiencyWeight);
        result.put("stance", stance());
        result.put("safetyDays", safetyDays);
        result.put("replenishLeadDays", replenishLeadDays);
        return result;
    }

    public String stance() {
        int cmp = costWeight.compareTo(efficiencyWeight);
        if (cmp > 0) {
            return "COST";
        }
        if (cmp < 0) {
            return "EFFICIENCY";
        }
        return "BALANCED";
    }

    static BigDecimal clamp(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.valueOf(0.5).setScale(4, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    static int clampDays(Integer value) {
        if (value == null || value < 1) {
            return 3;
        }
        if (value > 30) {
            return 30;
        }
        return value;
    }

    private BigDecimal read(String code, BigDecimal fallback) {
        CtSetting row = settings.selectOne(new LambdaQueryWrapper<CtSetting>()
                .eq(CtSetting::getCode, code)
                .last("LIMIT 1"));
        if (row == null || row.getSettingValue() == null || row.getSettingValue().trim().isEmpty()) {
            return clamp(fallback);
        }
        try {
            return clamp(new BigDecimal(row.getSettingValue().trim()));
        } catch (NumberFormatException ex) {
            return clamp(fallback);
        }
    }

    private int readInt(String code, int fallback) {
        CtSetting row = settings.selectOne(new LambdaQueryWrapper<CtSetting>()
                .eq(CtSetting::getCode, code)
                .last("LIMIT 1"));
        if (row == null || row.getSettingValue() == null || row.getSettingValue().trim().isEmpty()) {
            return clampDays(fallback);
        }
        try {
            return clampDays(new BigDecimal(row.getSettingValue().trim()).intValue());
        } catch (NumberFormatException ex) {
            return clampDays(fallback);
        }
    }

    private void write(String code, BigDecimal value) {
        write(code, value.toPlainString());
    }

    private void write(String code, int value) {
        write(code, Integer.toString(value));
    }

    private void write(String code, String value) {
        CtSetting row = settings.selectOne(new LambdaQueryWrapper<CtSetting>()
                .eq(CtSetting::getCode, code)
                .last("LIMIT 1"));
        if (row == null) {
            row = new CtSetting();
            row.setCode(code);
            row.setSettingValue(value);
            settings.insert(row);
        } else {
            row.setSettingValue(value);
            settings.updateById(row);
        }
    }
}
