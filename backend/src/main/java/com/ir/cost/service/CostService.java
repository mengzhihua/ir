package com.ir.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.cost.entity.CtCostTarget;
import com.ir.cost.mapper.CtCostTargetMapper;
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.mapper.CostRecordMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CostService {
    private final CostRecordMapper costMapper;
    private final CtCostTargetMapper targetMapper;
    private final CtActionMapper actionMapper;
    private final OrderSnapshotMapper orderMapper;
    private final ObjectMapper objectMapper;

    public CostService(
            CostRecordMapper costMapper,
            CtCostTargetMapper targetMapper,
            CtActionMapper actionMapper,
            OrderSnapshotMapper orderMapper,
            ObjectMapper objectMapper) {
        this.costMapper = costMapper;
        this.targetMapper = targetMapper;
        this.actionMapper = actionMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> summary(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<CostRecord> raw = records(
                null, null, null, null, from, LocalDate.now());
        List<CostRecord> rows = preferSettlement(raw);
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
        result.put("freightSource", freightSource(rows));
        result.put("freightBms", freightAmount(raw, "BMS"));
        result.put("freightTms", freightAmount(raw, "TMS"));
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
        BigDecimal estimated = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal writtenExpected = BigDecimal.ZERO;
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (CtAction action : actionMapper.selectList(
                new LambdaQueryWrapper<CtAction>()
                        .eq(CtAction::getStatus, "SUCCESS"))) {
            BigDecimal expected = action.getExpectedSaving() == null
                    ? BigDecimal.ZERO : action.getExpectedSaving();
            estimated = estimated.add(expected);
            BigDecimal written = writtenSaving(action);
            if (written != null) {
                actual = actual.add(written);
                writtenExpected = writtenExpected.add(expected);
            }
            String month = action.getCreatedAt() == null
                    ? "unknown" : action.getCreatedAt().toLocalDate().toString()
                    .substring(0, 7);
            add(byMonth, month, expected);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", estimated);
        result.put("actual", actual);
        result.put("variance", actual.subtract(writtenExpected));
        result.put("byMonth", byMonth);
        result.put("estimated", true);
        result.put("freightSource", freightSource(costMapper.selectList(null)));
        return result;
    }

    public static List<CostRecord> preferSettlement(List<CostRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<CostRecord>();
        }
        Set<String> settled = new HashSet<String>();
        for (CostRecord row : rows) {
            if (freight(row) && "BMS".equals(row.getSourceSystem())) {
                settled.add(settleKey(row));
            }
        }
        List<CostRecord> preferred = new ArrayList<CostRecord>();
        for (CostRecord row : rows) {
            if (!freight(row)) {
                preferred.add(row);
                continue;
            }
            if ("BMS".equals(row.getSourceSystem())) {
                preferred.add(row);
                continue;
            }
            if ((row.getSourceSystem() == null || "TMS".equals(row.getSourceSystem()))
                    && !settled.contains(settleKey(row))) {
                preferred.add(row);
            }
        }
        return preferred;
    }

    static String settleKey(CostRecord row) {
        if (row.getOrderNo() != null && !row.getOrderNo().trim().isEmpty()) {
            return "O:" + row.getOrderNo().trim();
        }
        String date = row.getBizDate() == null ? "" : row.getBizDate().toString();
        String warehouse = row.getWarehouseCode() == null ? "" : row.getWarehouseCode();
        String carrier = row.getCarrierCode() == null ? "" : row.getCarrierCode();
        return "F:" + date + "|" + warehouse + "|" + carrier;
    }

    public static boolean freight(CostRecord row) {
        return row != null && freightType(row.getCostType());
    }

    public static boolean freightType(String costType) {
        return "FREIGHT".equals(costType) || "TRANSPORT".equals(costType);
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

    private BigDecimal writtenSaving(CtAction action) {
        try {
            Map<String, Object> params = objectMapper.readValue(
                    action.getParamsJson() == null ? "{}" : action.getParamsJson(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object value = params.get("actualSaving");
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                return null;
            }
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String freightSource(List<CostRecord> rows) {
        boolean bms = freightAmount(rows, "BMS").signum() > 0;
        boolean tms = freightAmount(rows, "TMS").signum() > 0;
        if (bms && tms) {
            return "MIXED";
        }
        if (bms) {
            return "BMS";
        }
        if (tms) {
            return "TMS";
        }
        return "NONE";
    }

    private static BigDecimal freightAmount(List<CostRecord> rows, String source) {
        BigDecimal total = BigDecimal.ZERO;
        if (rows == null) {
            return total;
        }
        for (CostRecord row : rows) {
            if (freight(row) && source.equals(row.getSourceSystem())
                    && row.getAmount() != null) {
                total = total.add(row.getAmount());
            }
        }
        return total;
    }
}
