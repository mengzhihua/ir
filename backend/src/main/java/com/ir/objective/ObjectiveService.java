package com.ir.objective;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.common.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObjectiveService {
    private final CtObjectiveMapper mapper;
    private final MetricService metrics;

    public ObjectiveService(CtObjectiveMapper mapper, MetricService metrics) {
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public List<CtObjective> list() {
        return mapper.selectList(new LambdaQueryWrapper<CtObjective>().orderByDesc(CtObjective::getWeight));
    }

    public CtObjective save(CtObjective objective) {
        if (objective.getCode() == null || objective.getMetric() == null || objective.getTargetValue() == null) {
            throw new BizException("目标编码、指标与目标值不能为空");
        }
        if (objective.getDirection() == null) {
            objective.setDirection("MAX");
        }
        if (objective.getWeight() == null) {
            objective.setWeight(BigDecimal.TEN);
        }
        if (objective.getEnabled() == null) {
            objective.setEnabled(true);
        }
        CtObjective existing = mapper.selectOne(
                new LambdaQueryWrapper<CtObjective>().eq(CtObjective::getCode, objective.getCode()));
        if (existing != null && !existing.getId().equals(objective.getId())) {
            objective.setId(existing.getId());
        }
        if (objective.getId() == null) {
            mapper.insert(objective);
        } else {
            mapper.updateById(objective);
        }
        return mapper.selectById(objective.getId());
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 目标达成看板:每个目标的实际值、达成率、差距与状态,以及加权综合得分(0-100)。
     */
    public Map<String, Object> scoreboard() {
        Map<String, Object> current = metrics.metrics();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (CtObjective objective : list()) {
            if (!Boolean.TRUE.equals(objective.getEnabled())) {
                continue;
            }
            BigDecimal actual = number(current.get(objective.getMetric()));
            BigDecimal attainment = attainment(objective, actual);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", objective.getId());
            row.put("code", objective.getCode());
            row.put("name", objective.getName());
            row.put("category", objective.getCategory());
            row.put("metric", objective.getMetric());
            row.put("direction", objective.getDirection());
            row.put("unit", objective.getUnit());
            row.put("weight", objective.getWeight());
            row.put("target", objective.getTargetValue());
            row.put("actual", actual);
            row.put("gap", actual.subtract(objective.getTargetValue()));
            row.put("attainment", attainment);
            row.put("status", status(attainment));
            rows.add(row);
            weightSum = weightSum.add(objective.getWeight());
            weighted = weighted.add(attainment.multiply(objective.getWeight()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", weightSum.signum() == 0 ? BigDecimal.ZERO
                : weighted.multiply(BigDecimal.valueOf(100))
                .divide(weightSum, 1, RoundingMode.HALF_UP));
        result.put("objectives", rows);
        result.put("metrics", current);
        return result;
    }

    static BigDecimal attainment(CtObjective objective, BigDecimal actual) {
        BigDecimal target = objective.getTargetValue();
        BigDecimal value;
        if ("MIN".equals(objective.getDirection())) {
            if (actual.signum() <= 0) {
                value = BigDecimal.ONE;
            } else {
                value = target.divide(actual, 4, RoundingMode.HALF_UP);
            }
        } else {
            if (target.signum() == 0) {
                value = actual.signum() >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (target.signum() > 0) {
                value = actual.divide(target, 4, RoundingMode.HALF_UP);
            } else {
                value = BigDecimal.ONE;
            }
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value.max(BigDecimal.ZERO);
    }

    static String status(BigDecimal attainment) {
        if (attainment.compareTo(BigDecimal.valueOf(0.95)) >= 0) {
            return "ON_TRACK";
        }
        if (attainment.compareTo(BigDecimal.valueOf(0.8)) >= 0) {
            return "AT_RISK";
        }
        return "OFF_TRACK";
    }

    static BigDecimal number(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
