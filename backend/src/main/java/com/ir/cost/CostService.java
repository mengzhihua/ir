package com.ir.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.action.CtAction;
import com.ir.action.CtActionMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.OrderSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CostService {
    private final CostRecordMapper costMapper;
    private final CtCostTargetMapper targetMapper;
    private final CtActionMapper actionMapper;
    private final OrderSnapshotMapper orderMapper;

    public CostService(
            CostRecordMapper costMapper,
            CtCostTargetMapper targetMapper,
            CtActionMapper actionMapper,
            OrderSnapshotMapper orderMapper) {
        this.costMapper = costMapper;
        this.targetMapper = targetMapper;
        this.actionMapper = actionMapper;
        this.orderMapper = orderMapper;
    }

    public Map<String, Object> summary(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<CostRecord> rows = records(
                null, null, null, null, from, LocalDate.now());
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        Map<String, BigDecimal> byWarehouse = new LinkedHashMap<>();
        Map<String, BigDecimal> byCarrier = new LinkedHashMap<>();
        Map<String, BigDecimal> trend = new LinkedHashMap<>();
        for (CostRecord row : rows) {
            total = total.add(row.getAmount());
            add(byType, row.getCostType(), row.getAmount());
            add(byWarehouse, row.getWarehouseCode(), row.getAmount());
            add(byCarrier, row.getCarrierCode(), row.getAmount());
            add(trend, row.getBizDate().toString(), row.getAmount());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("byType", byType);
        result.put("byWarehouse", byWarehouse);
        result.put("byCarrier", byCarrier);
        result.put("costPerOrder", orderMapper.selectCount(null) == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(orderMapper.selectCount(null)),
                2, RoundingMode.HALF_UP));
        result.put("trend", trend);
        result.put("targetVsActual", targetVsActual(total));
        return result;
    }

    public Page<CostRecord> page(
            String orderNo,
            String costType,
            String warehouseCode,
            String carrierCode,
            LocalDate from,
            LocalDate to,
            long current,
            long size) {
        LambdaQueryWrapper<CostRecord> query = query(
                orderNo, costType, warehouseCode, carrierCode, from, to);
        query.orderByDesc(CostRecord::getBizDate);
        return costMapper.selectPage(new Page<>(current, size), query);
    }

    private List<CostRecord> records(
            String orderNo,
            String costType,
            String warehouseCode,
            String carrierCode,
            LocalDate from,
            LocalDate to) {
        LambdaQueryWrapper<CostRecord> query = query(
                orderNo, costType, warehouseCode, carrierCode, from, to);
        query.orderByDesc(CostRecord::getBizDate);
        return costMapper.selectList(query);
    }

    private LambdaQueryWrapper<CostRecord> query(
            String orderNo,
            String costType,
            String warehouseCode,
            String carrierCode,
            LocalDate from,
            LocalDate to) {
        LambdaQueryWrapper<CostRecord> query = new LambdaQueryWrapper<>();
        if (orderNo != null) {
            query.eq(CostRecord::getOrderNo, orderNo);
        }
        if (costType != null) {
            query.eq(CostRecord::getCostType, costType);
        }
        if (warehouseCode != null) {
            query.eq(CostRecord::getWarehouseCode, warehouseCode);
        }
        if (carrierCode != null) {
            query.eq(CostRecord::getCarrierCode, carrierCode);
        }
        if (from != null) {
            query.ge(CostRecord::getBizDate, from);
        }
        if (to != null) {
            query.le(CostRecord::getBizDate, to);
        }
        return query;
    }

    public Map<String, Object> saving() {
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (CtAction action : actionMapper.selectList(
                new LambdaQueryWrapper<CtAction>()
                        .eq(CtAction::getStatus, "SUCCESS"))) {
            BigDecimal value = action.getExpectedSaving() == null
                    ? BigDecimal.ZERO : action.getExpectedSaving();
            total = total.add(value);
            String month = action.getCreatedAt() == null
                    ? "unknown" : action.getCreatedAt().toLocalDate().toString()
                    .substring(0, 7);
            add(byMonth, month, value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("byMonth", byMonth);
        return result;
    }

    public List<CtCostTarget> targets() {
        return targetMapper.selectList(
                new LambdaQueryWrapper<CtCostTarget>().orderByDesc(
                        CtCostTarget::getMonth));
    }

    public CtCostTarget saveTarget(CtCostTarget target) {
        CtCostTarget existing = targetMapper.selectOne(
                new LambdaQueryWrapper<CtCostTarget>()
                        .eq(CtCostTarget::getMonth, target.getMonth())
                        .eq(CtCostTarget::getCostType, target.getCostType()));
        if (existing == null) {
            targetMapper.insert(target);
        } else {
            target.setId(existing.getId());
            targetMapper.updateById(target);
        }
        return target;
    }

    public void deleteTarget(Long id) {
        targetMapper.deleteById(id);
    }

    private Map<String, BigDecimal> targetVsActual(BigDecimal actual) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        List<CtCostTarget> targets = targetMapper.selectList(
                new LambdaQueryWrapper<CtCostTarget>()
                        .eq(CtCostTarget::getMonth,
                                LocalDate.now().toString().substring(0, 7)));
        BigDecimal target = BigDecimal.ZERO;
        for (CtCostTarget row : targets) {
            target = target.add(row.getTargetAmount());
        }
        result.put("target", target);
        result.put("actual", actual);
        result.put("variance", actual.subtract(target));
        return result;
    }

    private void add(
            Map<String, BigDecimal> values,
            String key,
            BigDecimal value) {
        String normalized = key == null ? "UNKNOWN" : key;
        values.put(normalized,
                values.getOrDefault(normalized, BigDecimal.ZERO).add(value));
    }
}
