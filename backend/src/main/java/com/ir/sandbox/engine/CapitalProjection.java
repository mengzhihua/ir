package com.ir.sandbox.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 大规模资金盘把 2x/5x 从常态结果线性投影，避免同一目录连跑六遍引擎。
 */
public final class CapitalProjection {
    public static final int LARGE_SKU = 10_000;
    private static final BigDecimal TIGHT = new BigDecimal("0.80");
    private static final BigDecimal FIVE = BigDecimal.valueOf(5);

    private CapitalProjection() {
    }

    public static boolean canProjectStress(
            SandboxEngine.Result normal, int skuCount, BigDecimal capital) {
        if (skuCount < LARGE_SKU || normal == null || capital == null) {
            return false;
        }
        if (!"RELIABLE".equals(normal.getCapitalVerdict())) {
            return false;
        }
        if (nz(normal.getStockoutUnits()).signum() != 0
                || nz(normal.getDeferredPurchaseQty()).signum() != 0
                || nz(normal.getCapitalShortage()).signum() != 0) {
            return false;
        }
        return nz(normal.getCashUsed()).multiply(FIVE)
                .compareTo(capital.multiply(TIGHT)) < 0;
    }

    public static SandboxEngine.Result scaleDemand(
            SandboxEngine.Result base, BigDecimal multiplier, BigDecimal capital) {
        SandboxEngine.Result row = new SandboxEngine.Result();
        BigDecimal factor = multiplier == null ? BigDecimal.ONE : multiplier;
        row.setSkuCount(base.getSkuCount());
        row.setInventoryUnits(nz(base.getInventoryUnits()));
        row.setInventoryValue(nz(base.getInventoryValue()));
        row.setServiceLevel(base.getServiceLevel());
        row.setStockoutUnits(nz(base.getStockoutUnits()));
        row.setAvgLeadDays(nz(base.getAvgLeadDays()));
        row.setDeferredPurchaseQty(nz(base.getDeferredPurchaseQty()));
        row.setCapitalShortage(nz(base.getCapitalShortage()));
        BigDecimal used = round(nz(base.getCashUsed()).multiply(factor));
        row.setCashUsed(used);
        row.setPurchaseCash(round(nz(base.getPurchaseCash()).multiply(factor)));
        row.setOpsCash(round(nz(base.getOpsCash()).multiply(factor)));
        row.setTotalCost(round(nz(base.getTotalCost()).multiply(factor)));
        row.setWorkingCapital(round(nz(capital)));
        row.setCashRemaining(round(nz(capital).subtract(used).max(BigDecimal.ZERO)));
        BigDecimal cap = nz(capital);
        BigDecimal util = cap.signum() == 0
                ? BigDecimal.ONE
                : used.divide(cap, 6, RoundingMode.HALF_UP);
        row.setCapitalUtilization(util.setScale(4, RoundingMode.HALF_UP));
        boolean feasible = used.compareTo(cap) <= 0
                && nz(row.getDeferredPurchaseQty()).signum() == 0
                && nz(row.getCapitalShortage()).signum() == 0;
        row.setCapitalFeasible(feasible);
        if (!feasible) {
            row.setCapitalVerdict("INSUFFICIENT");
            row.setCapitalReason("资金盘覆盖不了采购或履约现金，补货被推迟或出现现金缺口");
        } else if (util.compareTo(TIGHT) >= 0) {
            row.setCapitalVerdict("TIGHT");
            row.setCapitalReason("资金盘能撑住，但现金占用已超过 80%");
        } else {
            row.setCapitalVerdict("RELIABLE");
            row.setCapitalReason("资金盘覆盖履约与补货现金，占用低于 80%");
        }
        return row;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal round(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }
}
