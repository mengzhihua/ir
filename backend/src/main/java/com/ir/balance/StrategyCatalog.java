package com.ir.balance;

import com.ir.snapshot.CostRecord;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.ShipmentSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平衡策略目录:每个策略读取快照与目标达成情况,产出跨系统决策。
 */
@Component
public class StrategyCatalog {
    public static final List<Map<String, Object>> CATALOG = Arrays.asList(
            describe("STOCK_REBALANCE", "库存再平衡", "INVENTORY/SERVICE",
                    "可用库存低于安全库存时,优先从有富余的仓库调拨(WMS),否则向 SRM 生成采购申请"),
            describe("SUPPLY_EXPEDITE", "供应催单", "SUPPLY",
                    "低库存 SKU 对应的采购订单到货延误时,向 SRM 发起催单"),
            describe("DELAY_RECOVERY", "延误挽回", "SERVICE/NPS",
                    "在途已超预计到达的运单同步轨迹;严重延误且承运商准时率差时建议切换承运商"),
            describe("CARRIER_COST_OPTIMIZE", "承运商降本", "COST",
                    "待发运运单若存在更便宜且准时率不低于当前承运商的替代方案,则切换承运商"),
            describe("ORDER_UNBLOCK", "卡单疏通", "SERVICE",
                    "审核态订单停留过久时自动触发 OMS 自动处理"),
            describe("WAREHOUSE_REROUTE", "仓库分流", "COST/SERVICE",
                    "待履约订单所在仓库缺货而其他仓库有货时,改派发货仓"));

    public List<Decision> generate(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        result.addAll(stockRebalance(ctx));
        result.addAll(supplyExpedite(ctx));
        result.addAll(delayRecovery(ctx));
        result.addAll(carrierCostOptimize(ctx));
        result.addAll(orderUnblock(ctx));
        result.addAll(warehouseReroute(ctx));
        return result;
    }

    List<Decision> stockRebalance(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        Map<String, List<InventorySnapshot>> bySku = new LinkedHashMap<>();
        for (InventorySnapshot item : ctx.getInventory()) {
            if ("WMS".equals(item.getSourceSystem())) {
                bySku.computeIfAbsent(item.getSku(), k -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<String, List<InventorySnapshot>> entry : bySku.entrySet()) {
            for (InventorySnapshot low : entry.getValue()) {
                if (low.getQtyAvailable().compareTo(low.getSafetyQty()) >= 0) {
                    continue;
                }
                BigDecimal need = low.getSafetyQty().multiply(BigDecimal.valueOf(2))
                        .subtract(low.getQtyAvailable()).setScale(0, RoundingMode.CEILING);
                InventorySnapshot donor = null;
                for (InventorySnapshot other : entry.getValue()) {
                    if (other == low) {
                        continue;
                    }
                    BigDecimal surplus = other.getQtyAvailable().subtract(other.getSafetyQty());
                    if (surplus.compareTo(need) >= 0
                            && (donor == null || surplus.compareTo(
                            donor.getQtyAvailable().subtract(donor.getSafetyQty())) > 0)) {
                        donor = other;
                    }
                }
                Decision d = new Decision();
                d.setStrategy("STOCK_REBALANCE");
                d.setObjectiveCode("STOCKOUT_RATE");
                d.setPriority(ctx.priority("STOCKOUT_RATE", "NPS"));
                d.setExpectedNpsDelta(BigDecimal.valueOf(1.5));
                if (donor != null) {
                    d.setActionType("WMS_REPLENISH");
                    d.setTargetKey(low.getWarehouseCode());
                    d.param("warehouseCode", low.getWarehouseCode())
                            .param("fromWarehouseCode", donor.getWarehouseCode())
                            .param("sku", low.getSku())
                            .param("qty", need);
                    d.setExpectedCostDelta(need.multiply(ctx.getConfig().getTransferCostPerUnit()));
                    d.setRiskLevel("LOW");
                    d.setReason(String.format("%s 在 %s 可用 %s 低于安全库存 %s,从 %s 调拨 %s",
                            low.getSku(), low.getWarehouseCode(), low.getQtyAvailable().stripTrailingZeros().toPlainString(),
                            low.getSafetyQty().stripTrailingZeros().toPlainString(),
                            donor.getWarehouseCode(), need.toPlainString()));
                } else {
                    d.setActionType("SRM_PURCHASE_SUGGEST");
                    d.setTargetKey(low.getSku());
                    BigDecimal amount = need.multiply(unitCost(ctx, low.getSku()));
                    d.param("sku", low.getSku()).param("qty", need)
                            .param("plantCode", "1000")
                            .param("reason", "控制塔自动补货:" + low.getWarehouseCode() + " 低于安全库存");
                    d.setExpectedCostDelta(amount);
                    d.setRiskLevel(amount.compareTo(ctx.getConfig().getAutoPurchaseAmountLimit()) > 0
                            ? "HIGH" : "MEDIUM");
                    d.setApprovalRequired("HIGH".equals(d.getRiskLevel()));
                    d.setReason(String.format("%s 全网无可调拨富余库存,建议采购 %s(预计 %s 元)",
                            low.getSku(), need.toPlainString(), amount.setScale(0, RoundingMode.HALF_UP)));
                }
                result.add(d);
            }
        }
        return result;
    }

    List<Decision> supplyExpedite(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        List<String> lowSkus = new ArrayList<>();
        for (InventorySnapshot item : ctx.getInventory()) {
            if ("WMS".equals(item.getSourceSystem())
                    && item.getQtyAvailable().compareTo(item.getSafetyQty()) < 0) {
                lowSkus.add(item.getSku());
            }
        }
        LocalDate today = ctx.getNow().toLocalDate();
        for (PurchaseSnapshot asn : ctx.getPurchases()) {
            if (!"ASN".equals(asn.getDocType()) || asn.getRefCode() == null
                    || Arrays.asList("RECEIVED", "CANCELLED").contains(asn.getStatus())) {
                continue;
            }
            boolean late = "DELAYED".equals(asn.getStatus())
                    || (asn.getExpectedDate() != null && asn.getExpectedDate().isBefore(today));
            if (!late || !lowSkus.contains(asn.getSku())) {
                continue;
            }
            Decision d = new Decision();
            d.setStrategy("SUPPLY_EXPEDITE");
            d.setObjectiveCode("SUPPLIER_OTD");
            d.setActionType("SRM_EXPEDITE_PO");
            d.setTargetKey(asn.getRefCode());
            d.param("poCode", asn.getRefCode()).param("asnCode", asn.getCode())
                    .param("reason", asn.getSku() + " 低库存且到货延误");
            d.setRiskLevel("LOW");
            d.setExpectedNpsDelta(BigDecimal.ONE);
            d.setPriority(ctx.priority("SUPPLIER_OTD", "STOCKOUT_RATE"));
            d.setReason(String.format("ASN %s(供应商 %s)预计 %s 到货已延误,SKU %s 库存告急",
                    asn.getCode(), asn.getSupplierCode(), asn.getExpectedDate(), asn.getSku()));
            result.add(d);
        }
        return result;
    }

    List<Decision> delayRecovery(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        Map<String, double[]> carrierStats = carrierStats(ctx);
        String bestCarrier = bestOnTimeCarrier(carrierStats);
        for (ShipmentSnapshot s : ctx.getShipments()) {
            if (!"IN_TRANSIT".equals(s.getStatus()) || s.getPlannedArriveTime() == null
                    || !s.getPlannedArriveTime().isBefore(ctx.getNow())) {
                continue;
            }
            long delay = Duration.between(s.getPlannedArriveTime(), ctx.getNow()).toHours();
            Decision d = new Decision();
            d.setStrategy("DELAY_RECOVERY");
            d.setObjectiveCode("NPS");
            d.setPriority(ctx.priority("NPS", "OTIF") + Math.min(delay, 72) / 10.0);
            boolean severe = delay > 24 && Boolean.TRUE.equals(s.getExceptionFlag());
            double[] stat = carrierStats.get(s.getCarrierCode());
            if (severe && bestCarrier != null && !bestCarrier.equals(s.getCarrierCode())
                    && stat != null && stat[1] < 0.9) {
                d.setActionType("TMS_SWITCH_CARRIER");
                d.setTargetKey(s.getWaybillCode());
                d.param("waybillId", s.getWaybillCode()).param("carrierCode", bestCarrier);
                d.setRiskLevel("MEDIUM");
                d.setApprovalRequired(true);
                d.setExpectedNpsDelta(BigDecimal.valueOf(2));
                d.setReason(String.format("运单 %s 异常且延误 %d 小时,承运商 %s 准时率 %.0f%%,建议切换至 %s",
                        s.getWaybillCode(), delay, s.getCarrierCode(), stat[1] * 100, bestCarrier));
            } else {
                d.setActionType("TMS_SYNC_TRACK");
                d.setTargetKey(s.getWaybillCode());
                d.param("waybillId", s.getWaybillCode());
                d.setRiskLevel("LOW");
                d.setExpectedNpsDelta(BigDecimal.valueOf(0.5));
                d.setReason(String.format("运单 %s 已超预计到达 %d 小时,同步轨迹并触发客户通知", s.getWaybillCode(), delay));
            }
            result.add(d);
        }
        return result;
    }

    List<Decision> carrierCostOptimize(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        if (ctx.attainment("COST_PER_ORDER").compareTo(BigDecimal.valueOf(0.98)) >= 0) {
            return result;
        }
        Map<String, double[]> stats = carrierStats(ctx);
        for (ShipmentSnapshot s : ctx.getShipments()) {
            if (!Arrays.asList("CREATED", "DISPATCHED", "PENDING").contains(s.getStatus())) {
                continue;
            }
            double[] current = stats.get(s.getCarrierCode());
            if (current == null) {
                continue;
            }
            String cheaper = null;
            double[] cheaperStat = null;
            for (Map.Entry<String, double[]> e : stats.entrySet()) {
                if (e.getKey().equals(s.getCarrierCode()) || "SELF".equals(e.getKey())) {
                    continue;
                }
                if (e.getValue()[0] < current[0] * 0.9 && e.getValue()[1] >= current[1]
                        && (cheaperStat == null || e.getValue()[0] < cheaperStat[0])) {
                    cheaper = e.getKey();
                    cheaperStat = e.getValue();
                }
            }
            if (cheaper == null) {
                continue;
            }
            Decision d = new Decision();
            d.setStrategy("CARRIER_COST_OPTIMIZE");
            d.setObjectiveCode("COST_PER_ORDER");
            d.setActionType("TMS_SWITCH_CARRIER");
            d.setTargetKey(s.getWaybillCode());
            d.param("waybillId", s.getWaybillCode()).param("carrierCode", cheaper);
            d.setExpectedCostDelta(BigDecimal.valueOf(cheaperStat[0] - current[0]).setScale(2, RoundingMode.HALF_UP));
            d.setRiskLevel(ctx.serviceGuarded() ? "MEDIUM" : "LOW");
            d.setApprovalRequired(ctx.serviceGuarded());
            d.setPriority(ctx.priority("COST_PER_ORDER"));
            d.setReason(String.format("运单 %s 承运商 %s 均价 %.1f,切换 %s 均价 %.1f 且准时率不低于当前",
                    s.getWaybillCode(), s.getCarrierCode(), current[0], cheaper, cheaperStat[0]));
            result.add(d);
        }
        return result;
    }

    List<Decision> orderUnblock(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        for (OrderSnapshot o : ctx.getOrders()) {
            if (!"AUDITED".equals(o.getStatus())) {
                continue;
            }
            long hours = Duration.between(o.getOrderTime(), ctx.getNow()).toHours();
            if (hours < 4) {
                continue;
            }
            Decision d = new Decision();
            d.setStrategy("ORDER_UNBLOCK");
            d.setObjectiveCode("OTIF");
            d.setActionType("OMS_AUTO_PROCESS");
            d.setTargetKey(o.getOrderNo());
            d.param("orderNo", o.getOrderNo());
            d.setRiskLevel("LOW");
            d.setExpectedNpsDelta(BigDecimal.valueOf(0.5));
            d.setPriority(ctx.priority("OTIF", "NPS"));
            d.setReason(String.format("订单 %s 审核后 %d 小时未流转,自动推进", o.getOrderNo(), hours));
            result.add(d);
        }
        return result;
    }

    List<Decision> warehouseReroute(BalanceContext ctx) {
        List<Decision> result = new ArrayList<>();
        Map<String, InventorySnapshot> stock = new LinkedHashMap<>();
        for (InventorySnapshot item : ctx.getInventory()) {
            if ("WMS".equals(item.getSourceSystem())) {
                stock.put(item.getWarehouseCode() + "|" + item.getSku(), item);
            }
        }
        for (OrderSnapshot o : ctx.getOrders()) {
            if (!Arrays.asList("PAID", "CREATED").contains(o.getStatus()) || o.getQty() == null) {
                continue;
            }
            InventorySnapshot here = stock.get(o.getWarehouseCode() + "|" + o.getSku());
            if (here == null || here.getQtyAvailable().compareTo(o.getQty()) >= 0) {
                continue;
            }
            InventorySnapshot best = null;
            for (InventorySnapshot cand : stock.values()) {
                if (cand.getSku().equals(o.getSku()) && !cand.getWarehouseCode().equals(o.getWarehouseCode())
                        && cand.getQtyAvailable().subtract(o.getQty()).compareTo(cand.getSafetyQty()) >= 0
                        && (best == null || cand.getQtyAvailable().compareTo(best.getQtyAvailable()) > 0)) {
                    best = cand;
                }
            }
            if (best == null) {
                continue;
            }
            Decision d = new Decision();
            d.setStrategy("WAREHOUSE_REROUTE");
            d.setObjectiveCode("OTIF");
            d.setActionType("OMS_REROUTE_WAREHOUSE");
            d.setTargetKey(o.getOrderNo());
            d.param("orderNo", o.getOrderNo()).param("warehouseCode", best.getWarehouseCode());
            d.setRiskLevel("MEDIUM");
            d.setApprovalRequired(false);
            d.setExpectedNpsDelta(BigDecimal.ONE);
            d.setPriority(ctx.priority("OTIF", "NPS"));
            d.setReason(String.format("订单 %s 在 %s 缺货(可用 %s < 需求 %s),改派 %s 发货",
                    o.getOrderNo(), o.getWarehouseCode(), here.getQtyAvailable().stripTrailingZeros().toPlainString(),
                    o.getQty().stripTrailingZeros().toPlainString(), best.getWarehouseCode()));
            result.add(d);
        }
        return result;
    }

    /** carrier -> [avgFreight, onTimeRate]. */
    Map<String, double[]> carrierStats(BalanceContext ctx) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (CostRecord c : ctx.getFreightCosts()) {
            if (c.getCarrierCode() == null) {
                continue;
            }
            sums.computeIfAbsent(c.getCarrierCode(), k -> new double[2])[0] += c.getAmount().doubleValue();
            counts.computeIfAbsent(c.getCarrierCode(), k -> new int[2])[0]++;
        }
        for (ShipmentSnapshot s : ctx.getShipments()) {
            if (s.getCarrierCode() == null || !Arrays.asList("DELIVERED", "CLOSED").contains(s.getStatus())) {
                continue;
            }
            int[] count = counts.computeIfAbsent(s.getCarrierCode(), k -> new int[2]);
            double[] sum = sums.computeIfAbsent(s.getCarrierCode(), k -> new double[2]);
            count[1]++;
            if (s.getActualArriveTime() != null && s.getPlannedArriveTime() != null
                    && !s.getActualArriveTime().isAfter(s.getPlannedArriveTime())) {
                sum[1] += 1;
            }
            if (count[0] == 0 && s.getFreightAmount() != null) {
                sum[0] += s.getFreightAmount().doubleValue();
            }
        }
        Map<String, double[]> result = new LinkedHashMap<>();
        for (String carrier : sums.keySet()) {
            int[] count = counts.get(carrier);
            double[] sum = sums.get(carrier);
            int freightCount = count[0] > 0 ? count[0] : count[1];
            result.put(carrier, new double[]{
                    freightCount == 0 ? 0 : sum[0] / freightCount,
                    count[1] == 0 ? 1.0 : sum[1] / count[1]});
        }
        return result;
    }

    private static String bestOnTimeCarrier(Map<String, double[]> stats) {
        String best = null;
        for (Map.Entry<String, double[]> e : stats.entrySet()) {
            if ("SELF".equals(e.getKey())) {
                continue;
            }
            if (best == null || e.getValue()[1] > stats.get(best)[1]) {
                best = e.getKey();
            }
        }
        return best;
    }

    private static BigDecimal unitCost(BalanceContext ctx, String sku) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (PurchaseSnapshot po : ctx.getPurchases()) {
            if ("PO".equals(po.getDocType()) && sku.equals(po.getSku()) && po.getAmount() != null
                    && po.getQty() != null && po.getQty().signum() > 0) {
                total = total.add(po.getAmount().divide(po.getQty(), 2, RoundingMode.HALF_UP));
                count++;
            }
        }
        return count == 0 ? BigDecimal.valueOf(15) : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> describe(String code, String name, String objective, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("objective", objective);
        row.put("description", description);
        return row;
    }
}
