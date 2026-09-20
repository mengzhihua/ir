package com.ir.sandbox.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 资金盘档位：10 万 / 百万 / 千万 / 亿 / 十亿，并限制 SKU 与库存量上界。 */
public final class CapitalTiers {
    public static final int MAX_SKU = 100_000;
    public static final BigDecimal MAX_QTY = new BigDecimal("10000000");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000000");
    public static final int MAX_SKU_SUMMARY = 50;

    private CapitalTiers() {
    }

    public static List<Map<String, Object>> presets() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        rows.add(tier("100K", "10 万", "100000"));
        rows.add(tier("1M", "百万", "1000000"));
        rows.add(tier("10M", "千万", "10000000"));
        rows.add(tier("100M", "亿", "100000000"));
        rows.add(tier("1B", "十亿", "1000000000"));
        return rows;
    }

    public static List<BigDecimal> amounts() {
        List<BigDecimal> rows = new ArrayList<BigDecimal>();
        for (Map<String, Object> tier : presets()) {
            rows.add((BigDecimal) tier.get("amount"));
        }
        return rows;
    }

    public static String labelOf(BigDecimal amount) {
        if (amount == null) {
            return "未设";
        }
        for (Map<String, Object> tier : presets()) {
            if (amount.compareTo((BigDecimal) tier.get("amount")) == 0) {
                return String.valueOf(tier.get("label"));
            }
        }
        return "自定义";
    }

    public static BigDecimal clampAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return new BigDecimal("100000000");
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            return MAX_AMOUNT;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static int clampSku(Integer value) {
        if (value == null || value < 1) {
            return 1;
        }
        return Math.min(value, MAX_SKU);
    }

    public static BigDecimal clampQty(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(MAX_QTY) > 0) {
            return MAX_QTY;
        }
        return value;
    }

    private static Map<String, Object> tier(String code, String label, String amount) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("code", code);
        row.put("label", label);
        row.put("amount", new BigDecimal(amount));
        return row;
    }
}
