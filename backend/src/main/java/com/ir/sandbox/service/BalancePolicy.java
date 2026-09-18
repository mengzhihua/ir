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

/** 运行时成本/效率权重：启动读配置，看板可改，立刻作用于预警建议和自动沙盘。 */

@Service
public class BalancePolicy {
    public static final String COST_KEY = "sandbox.cost-weight";
    public static final String EFF_KEY = "sandbox.efficiency-weight";

    private final CtSettingMapper settings;
    private final BigDecimal defaultCost;
    private final BigDecimal defaultEfficiency;
    private volatile BigDecimal costWeight;
    private volatile BigDecimal efficiencyWeight;

    public BalancePolicy(
            CtSettingMapper settings,
            @Value("${ir.sandbox.cost-weight:0.5}") BigDecimal defaultCost,
            @Value("${ir.sandbox.efficiency-weight:0.5}") BigDecimal defaultEfficiency) {
        this.settings = settings;
        this.defaultCost = defaultCost;
        this.defaultEfficiency = defaultEfficiency;
        this.costWeight = clamp(defaultCost);
        this.efficiencyWeight = clamp(defaultEfficiency);
    }

    @PostConstruct
    public void load() {
        try {
            this.costWeight = read(COST_KEY, defaultCost);
            this.efficiencyWeight = read(EFF_KEY, defaultEfficiency);
        } catch (RuntimeException ex) {
            this.costWeight = clamp(defaultCost);
            this.efficiencyWeight = clamp(defaultEfficiency);
        }
    }

    public BigDecimal costWeight() {
        return costWeight;
    }

    public BigDecimal efficiencyWeight() {
        return efficiencyWeight;
    }

    public synchronized Map<String, Object> update(BigDecimal cost, BigDecimal efficiency) {
        this.costWeight = clamp(cost);
        this.efficiencyWeight = clamp(efficiency);
        write(COST_KEY, this.costWeight);
        write(EFF_KEY, this.efficiencyWeight);
        return snapshot();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("costWeight", costWeight);
        result.put("efficiencyWeight", efficiencyWeight);
        result.put("stance", stance());
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

    private void write(String code, BigDecimal value) {
        CtSetting row = settings.selectOne(new LambdaQueryWrapper<CtSetting>()
                .eq(CtSetting::getCode, code)
                .last("LIMIT 1"));
        if (row == null) {
            row = new CtSetting();
            row.setCode(code);
            row.setSettingValue(value.toPlainString());
            settings.insert(row);
        } else {
            row.setSettingValue(value.toPlainString());
            settings.updateById(row);
        }
    }
}
