package com.ir.sandbox.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 自动沙盘选优：先保服务水平和零缺货、再选占用现金最低；
 * 若 2 倍需求仍可行的方案现金不超过最便宜方案的 12%，则改推稳健方案。
 */
public final class AutoSandboxPicker {
    public static final BigDecimal CASH_SLACK = new BigDecimal("1.12");

    private AutoSandboxPicker() {
    }

    public static class Decision<T> {
        public T recommended;
        public T cashBest;
        public T robustBest;
        public boolean usedRobust;
        public Map<String, Object> rationale = new LinkedHashMap<String, Object>();
    }

    public static <T> Decision<T> decide(
            List<T> rows,
            Function<T, BigDecimal> serviceLevel,
            Function<T, BigDecimal> stockout,
            Function<T, BigDecimal> cash,
            Function<T, Boolean> robust,
            Function<T, String> name) {
        Decision<T> decision = new Decision<T>();
        if (rows == null || rows.isEmpty()) {
            decision.rationale.put("rule", "SERVICE_FIRST_CASH_ROBUST");
            decision.rationale.put("eligible", 0);
            decision.rationale.put("rejected", 0);
            decision.rationale.put("robustEligible", 0);
            decision.rationale.put("usedRobust", false);
            decision.rationale.put("reason", "没有可比较的自动方案。");
            return decision;
        }
        List<T> eligible = new ArrayList<T>();
        List<T> robustRows = new ArrayList<T>();
        for (T row : rows) {
            if (nz(serviceLevel.apply(row)).compareTo(ServiceFirstPicker.MIN_SERVICE) < 0) {
                continue;
            }
            if (nz(stockout.apply(row)).signum() > 0) {
                continue;
            }
            eligible.add(row);
            if (Boolean.TRUE.equals(robust.apply(row))) {
                robustRows.add(row);
            }
        }
        decision.cashBest = ServiceFirstPicker.pickByCash(
                eligible, serviceLevel, stockout, cash);
        decision.robustBest = ServiceFirstPicker.pickByCash(
                robustRows, serviceLevel, stockout, cash);
        decision.recommended = decision.cashBest;
        if (decision.cashBest != null && decision.robustBest != null) {
            BigDecimal cheapest = nz(cash.apply(decision.cashBest));
            BigDecimal robustCash = nz(cash.apply(decision.robustBest));
            BigDecimal cap = cheapest.multiply(CASH_SLACK).setScale(4, RoundingMode.HALF_UP);
            if (robustCash.compareTo(cap) <= 0) {
                decision.recommended = decision.robustBest;
                decision.usedRobust = decision.robustBest != decision.cashBest;
            }
        }
        if (decision.recommended == null) {
            decision.recommended = decision.cashBest;
        }

        Map<String, Object> rationale = decision.rationale;
        rationale.put("rule", "SERVICE_FIRST_CASH_ROBUST");
        rationale.put("eligible", eligible.size());
        rationale.put("rejected", rows.size() - eligible.size());
        rationale.put("robustEligible", robustRows.size());
        rationale.put("usedRobust", decision.usedRobust);
        rationale.put("cashSlack", CASH_SLACK);
        rationale.put("recommendedName", label(name, decision.recommended));
        rationale.put("cashBestName", label(name, decision.cashBest));
        rationale.put("robustBestName", label(name, decision.robustBest));
        if (decision.cashBest != null) {
            rationale.put("cashBestUsed", nz(cash.apply(decision.cashBest)));
        }
        if (decision.recommended != null) {
            rationale.put("recommendedCash", nz(cash.apply(decision.recommended)));
        }
        rationale.put("reason", reason(decision, eligible.size(), rows.size() - eligible.size()));
        return decision;
    }

    public static boolean stressReliable(BigDecimal serviceLevel, BigDecimal stockout) {
        return nz(serviceLevel).compareTo(ServiceFirstPicker.MIN_SERVICE) >= 0
                && nz(stockout).signum() <= 0;
    }

    private static <T> String reason(Decision<T> decision, int eligible, int rejected) {
        if (decision.recommended == null) {
            return "没有可比较的自动方案。";
        }
        if (eligible == 0) {
            return "没有方案同时满足服务水平 ≥ 99.5% 且零缺货，退回综合分最高的方案。";
        }
        if (decision.usedRobust) {
            return "服务水平 ≥ 99.5% 且零缺货的 "
                    + eligible
                    + " 个方案中，2 倍需求仍可行的方案现金不超过最便宜方案的 12%，改推稳健方案。"
                    + (rejected > 0 ? "另有 " + rejected + " 个方案因服务或缺货被淘汰。" : "");
        }
        if (decision.robustBest != null && decision.robustBest == decision.cashBest) {
            return "服务水平 ≥ 99.5% 且零缺货的 "
                    + eligible
                    + " 个方案中，占用现金最低的方案在 2 倍需求下仍然可行。";
        }
        if (decision.robustBest != null) {
            return "服务水平 ≥ 99.5% 且零缺货的 "
                    + eligible
                    + " 个方案中选占用现金最低的方案；2 倍需求稳健方案更贵，超过 12% 现金缓冲，仍推现金最低。";
        }
        return "服务水平 ≥ 99.5% 且零缺货的 "
                + eligible
                + " 个方案中选占用现金最低的方案；本轮没有方案能在 2 倍需求下保持零缺货。";
    }

    private static <T> String label(Function<T, String> name, T row) {
        if (row == null || name == null) {
            return null;
        }
        return name.apply(row);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
