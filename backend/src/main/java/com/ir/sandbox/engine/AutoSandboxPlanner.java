package com.ir.sandbox.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动沙盘候选：固定成本/效率网格，再按未关闭预警、仓网和当前立场追加派生方案。
 */
public final class AutoSandboxPlanner {
    private AutoSandboxPlanner() {
    }

    public static class Candidate {
        public final String name;
        public final ScenarioParams params;
        public final String source;
        public final String signal;

        public Candidate(String name, ScenarioParams params, String source, String signal) {
            this.name = name;
            this.params = params;
            this.source = source;
            this.signal = signal;
        }
    }

    public static List<Candidate> plan(
            Map<String, Integer> openAlerts,
            List<String> warehouses,
            String stance) {
        List<Candidate> result = new ArrayList<Candidate>();
        result.add(grid("自动·就近分配（效率优先）",
                params("NEAREST", null, 3, 3, 0.3, 0.7, mix(0.4, 0.3, 0.3))));
        result.add(grid("自动·最低成本",
                params("LOWEST_COST", null, 3, 3, 0.7, 0.3, mix(0.2, 0.3, 0.5))));
        result.add(grid("自动·成本效率均衡",
                params("BALANCED", null, 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(grid("自动·上海单仓",
                params("SINGLE_WAREHOUSE", "WH-SH", 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(grid("自动·短交期补货",
                params("BALANCED", null, 3, 1, 0.4, 0.6, mix(0.4, 0.3, 0.3))));
        result.add(grid("自动·低安全短交期",
                params("BALANCED", null, 1, 1, 0.4, 0.6, mix(0.4, 0.3, 0.3))));
        result.add(grid("自动·时效承运（顺丰为主）",
                params("NEAREST", null, 3, 2, 0.3, 0.7, mix(0.7, 0.2, 0.1))));
        result.add(grid("自动·经济承运",
                params("LOWEST_COST", null, 3, 4, 0.7, 0.3, mix(0.1, 0.3, 0.6))));
        result.add(grid("自动·高安全库存 + 短交期",
                params("BALANCED", null, 7, 1, 0.4, 0.6, mix(0.5, 0.3, 0.2))));
        result.add(grid("自动·低安全库存 + 最低成本",
                params("LOWEST_COST", null, 1, 5, 0.8, 0.2, mix(0.1, 0.2, 0.7))));

        if (warehouses != null) {
            for (String warehouse : warehouses) {
                if (warehouse == null || warehouse.trim().isEmpty() || "WH-SH".equals(warehouse)) {
                    continue;
                }
                result.add(new Candidate(
                        "自动·" + warehouseLabel(warehouse) + "单仓",
                        params("SINGLE_WAREHOUSE", warehouse, 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3)),
                        "WAREHOUSE",
                        warehouse));
            }
        }

        if (count(openAlerts, "TMS_DELAY", "DELAY") > 0) {
            result.add(new Candidate(
                    "自动·时效加码（预警）",
                    params("NEAREST", null, 3, 1, 0.25, 0.75, mix(0.8, 0.15, 0.05)),
                    "ALERT",
                    "TMS_DELAY"));
        }
        if (count(openAlerts, "COST_OVERRUN") > 0) {
            result.add(new Candidate(
                    "自动·运费压降（预警）",
                    params("LOWEST_COST", null, 1, 4, 0.8, 0.2, mix(0.05, 0.25, 0.7)),
                    "ALERT",
                    "COST_OVERRUN"));
        }
        if (count(openAlerts, "LOW_STOCK", "FORECAST_STOCKOUT", "SAP_LOW_STOCK") > 0) {
            result.add(new Candidate(
                    "自动·补货加速（预警）",
                    params("BALANCED", null, 2, 1, 0.4, 0.6, mix(0.5, 0.3, 0.2)),
                    "ALERT",
                    "LOW_STOCK"));
        }
        if (count(openAlerts, "ORDER_STUCK", "WMS_STUCK") > 0) {
            result.add(new Candidate(
                    "自动·卡单疏导（预警）",
                    params("NEAREST", null, 3, 2, 0.25, 0.75, mix(0.6, 0.3, 0.1)),
                    "ALERT",
                    "ORDER_STUCK"));
        }

        if ("COST".equals(stance)) {
            result.add(new Candidate(
                    "自动·立场·成本压降",
                    params("LOWEST_COST", null, 1, 4, 0.8, 0.2, mix(0.1, 0.2, 0.7)),
                    "STANCE",
                    "COST"));
        } else if ("EFFICIENCY".equals(stance)) {
            result.add(new Candidate(
                    "自动·立场·效率加码",
                    params("NEAREST", null, 3, 1, 0.2, 0.8, mix(0.8, 0.15, 0.05)),
                    "STANCE",
                    "EFFICIENCY"));
        }
        return result;
    }

    public static List<String> signalsOf(Map<String, Integer> openAlerts) {
        List<String> signals = new ArrayList<String>();
        addSignal(signals, openAlerts, "TMS_DELAY");
        addSignal(signals, openAlerts, "DELAY");
        addSignal(signals, openAlerts, "COST_OVERRUN");
        addSignal(signals, openAlerts, "LOW_STOCK");
        addSignal(signals, openAlerts, "FORECAST_STOCKOUT");
        addSignal(signals, openAlerts, "SAP_LOW_STOCK");
        addSignal(signals, openAlerts, "ORDER_STUCK");
        addSignal(signals, openAlerts, "WMS_STUCK");
        return signals;
    }

    static String warehouseLabel(String warehouse) {
        if ("WH-BJ".equals(warehouse)) {
            return "北京";
        }
        if ("WH-GZ".equals(warehouse)) {
            return "广州";
        }
        if ("WH-SH".equals(warehouse)) {
            return "上海";
        }
        return warehouse;
    }

    private static void addSignal(List<String> signals, Map<String, Integer> openAlerts, String type) {
        if (count(openAlerts, type) > 0) {
            signals.add(type);
        }
    }

    private static int count(Map<String, Integer> openAlerts, String... types) {
        if (openAlerts == null || types == null) {
            return 0;
        }
        int total = 0;
        for (String type : types) {
            Integer value = openAlerts.get(type);
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private static Candidate grid(String name, ScenarioParams params) {
        return new Candidate(name, params, "GRID", null);
    }

    private static ScenarioParams params(
            String allocation,
            String warehouse,
            int safetyDays,
            int replenishLeadDays,
            double costW,
            double efficiencyW,
            Map<String, BigDecimal> carrierMix) {
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(14);
        params.setAllocationStrategy(allocation);
        params.setSingleWarehouse(warehouse);
        params.setSafetyDays(safetyDays);
        params.setReplenishLeadDays(replenishLeadDays);
        params.setCostWeight(BigDecimal.valueOf(costW));
        params.setEfficiencyWeight(BigDecimal.valueOf(efficiencyW));
        params.setCarrierMix(carrierMix);
        return params;
    }

    private static Map<String, BigDecimal> mix(double sf, double jd, double self) {
        Map<String, BigDecimal> mix = new LinkedHashMap<String, BigDecimal>();
        mix.put("SF", BigDecimal.valueOf(sf));
        mix.put("JD", BigDecimal.valueOf(jd));
        mix.put("SELF01", BigDecimal.valueOf(self));
        return mix;
    }
}
