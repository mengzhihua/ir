package com.ir.sandbox.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * 先保服务水平，再在零缺货方案里选占用现金最低的。
 * 资金盘 playbook 和自动沙盘共用，避免一边推短交期、一边推经济承运。
 */
public final class ServiceFirstPicker {
    public static final BigDecimal MIN_SERVICE = new BigDecimal("0.995");

    private ServiceFirstPicker() {
    }

    public static <T> T pickByCash(
            List<T> rows,
            Function<T, BigDecimal> serviceLevel,
            Function<T, BigDecimal> stockout,
            Function<T, BigDecimal> cash) {
        T best = null;
        BigDecimal bestCash = null;
        if (rows == null) {
            return null;
        }
        for (T row : rows) {
            if (nz(serviceLevel.apply(row)).compareTo(MIN_SERVICE) < 0) {
                continue;
            }
            if (nz(stockout.apply(row)).signum() > 0) {
                continue;
            }
            BigDecimal used = nz(cash.apply(row));
            if (best == null || used.compareTo(bestCash) < 0) {
                best = row;
                bestCash = used;
            }
        }
        return best;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
