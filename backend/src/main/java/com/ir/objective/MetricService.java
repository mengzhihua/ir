package com.ir.objective;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.alert.CtAlert;
import com.ir.alert.CtAlertMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.FinanceSnapshotMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.SupplierScore;
import com.ir.snapshot.SupplierScoreMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一指标计算:成本、服务、库存、供应四类指标,以及基于运营数据的 NPS 估算。
 */
@Service
public class MetricService {
    private static final List<String> DONE = Arrays.asList("DELIVERED", "CLOSED");

    private final OrderSnapshotMapper orders;
    private final ShipmentSnapshotMapper shipments;
    private final InventorySnapshotMapper inventory;
    private final CostRecordMapper costs;
    private final PurchaseSnapshotMapper purchases;
    private final SupplierScoreMapper supplierScores;
    private final FinanceSnapshotMapper finance;
    private final CtAlertMapper alerts;

    public MetricService(
            OrderSnapshotMapper orders,
            ShipmentSnapshotMapper shipments,
            InventorySnapshotMapper inventory,
            CostRecordMapper costs,
            PurchaseSnapshotMapper purchases,
            SupplierScoreMapper supplierScores,
            FinanceSnapshotMapper finance,
            CtAlertMapper alerts) {
        this.orders = orders;
        this.shipments = shipments;
        this.inventory = inventory;
        this.costs = costs;
        this.purchases = purchases;
        this.supplierScores = supplierScores;
        this.finance = finance;
        this.alerts = alerts;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate from = LocalDate.now().minusDays(29);
        List<OrderSnapshot> orderRows = orders.selectList(null);
        List<ShipmentSnapshot> shipmentRows = shipments.selectList(null);

        BigDecimal totalCost = BigDecimal.ZERO;
        for (CostRecord cost : costs.selectList(new LambdaQueryWrapper<CostRecord>()
                .ge(CostRecord::getBizDate, from))) {
            totalCost = totalCost.add(cost.getAmount());
        }
        int cancelled = 0;
        BigDecimal leadTotal = BigDecimal.ZERO;
        int leadCount = 0;
        for (OrderSnapshot order : orderRows) {
            if ("CANCELLED".equals(order.getStatus())) {
                cancelled++;
            }
            if (order.getCompleteTime() != null) {
                leadTotal = leadTotal.add(BigDecimal.valueOf(Duration.between(
                        order.getOrderTime(), order.getCompleteTime()).toHours()));
                leadCount++;
            }
        }
        result.put("orderCount", orderRows.size());
        result.put("totalCost30d", totalCost);
        result.put("costPerOrder30d", ratio(totalCost, orderRows.size(), 2));
        result.put("cancelRate", ratio(BigDecimal.valueOf(cancelled), orderRows.size(), 4));
        result.put("avgLeadTimeHours", ratio(leadTotal, leadCount, 2));

        Map<String, Object> nps = nps(orderRows, shipmentRows);
        result.putAll(nps);

        int lowStock = 0;
        int stockout = 0;
        int skuCount = 0;
        for (InventorySnapshot item : inventory.selectList(new LambdaQueryWrapper<InventorySnapshot>()
                .ne(InventorySnapshot::getSourceSystem, "SAP"))) {
            skuCount++;
            if (item.getQtyAvailable().compareTo(item.getSafetyQty()) < 0) {
                lowStock++;
            }
            if (item.getQtyAvailable().signum() <= 0) {
                stockout++;
            }
        }
        result.put("lowStockSkus", lowStock);
        result.put("stockoutRate", ratio(BigDecimal.valueOf(stockout), skuCount, 4));
        result.put("lowStockRate", ratio(BigDecimal.valueOf(lowStock), skuCount, 4));

        result.putAll(supply());
        for (FinanceSnapshot row : finance.selectList(null)) {
            result.put("sap" + camel(row.getMetric()), row.getAmount());
        }
        result.put("openHighAlerts", alerts.selectCount(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN").eq(CtAlert::getSeverity, "HIGH")));
        return result;
    }

    /**
     * NPS 估算:以每单履约体验推断推荐者/贬损者。
     * 准时送达且无异常 => 推荐者;延误 > 24h、异常件、付款后取消 => 贬损者;其余为中立。
     */
    public Map<String, Object> nps(List<OrderSnapshot> orderRows, List<ShipmentSnapshot> shipmentRows) {
        LocalDateTime now = LocalDateTime.now();
        int promoters = 0;
        int detractors = 0;
        int passives = 0;
        int deliveredOnTime = 0;
        int deliveredTotal = 0;
        int lateHours = 0;
        for (ShipmentSnapshot s : shipmentRows) {
            boolean done = DONE.contains(s.getStatus());
            LocalDateTime planned = s.getPlannedArriveTime();
            LocalDateTime actual = done ? (s.getActualArriveTime() == null ? now : s.getActualArriveTime()) : now;
            long delay = planned == null ? 0 : Duration.between(planned, actual).toHours();
            boolean exception = Boolean.TRUE.equals(s.getExceptionFlag()) || "EXCEPTION".equals(s.getStatus());
            if (done) {
                deliveredTotal++;
                if (delay <= 0 && !exception) {
                    deliveredOnTime++;
                }
            }
            if (exception || delay > 24) {
                detractors++;
                lateHours += Math.max(0, delay);
            } else if (done && delay <= 0) {
                promoters++;
            } else {
                passives++;
            }
        }
        for (OrderSnapshot o : orderRows) {
            if ("CANCELLED".equals(o.getStatus()) && o.getPayTime() != null) {
                detractors++;
            }
        }
        int total = promoters + detractors + passives;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("npsEstimate", total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((promoters - detractors) * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        result.put("npsPromoters", promoters);
        result.put("npsPassives", passives);
        result.put("npsDetractors", detractors);
        result.put("npsSample", total);
        result.put("otif30d", ratio(BigDecimal.valueOf(deliveredOnTime), deliveredTotal, 4));
        result.put("avgDelayHoursDetractor", ratio(BigDecimal.valueOf(lateHours), detractors, 1));
        return result;
    }

    private Map<String, Object> supply() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<PurchaseSnapshot> asns = purchases.selectList(new LambdaQueryWrapper<PurchaseSnapshot>()
                .eq(PurchaseSnapshot::getDocType, "ASN"));
        LocalDate today = LocalDate.now();
        int onTime = 0;
        int closed = 0;
        int delayedOpen = 0;
        for (PurchaseSnapshot asn : asns) {
            if ("RECEIVED".equals(asn.getStatus())) {
                closed++;
                if (asn.getReceivedAt() == null || asn.getExpectedDate() == null
                        || !asn.getReceivedAt().toLocalDate().isAfter(asn.getExpectedDate())) {
                    onTime++;
                }
            } else if (!"CANCELLED".equals(asn.getStatus()) && asn.getExpectedDate() != null
                    && asn.getExpectedDate().isBefore(today)) {
                delayedOpen++;
            }
        }
        long openPo = purchases.selectCount(new LambdaQueryWrapper<PurchaseSnapshot>()
                .eq(PurchaseSnapshot::getDocType, "PO")
                .notIn(PurchaseSnapshot::getStatus, "CLOSED", "CANCELLED"));
        List<SupplierScore> scores = supplierScores.selectList(null);
        BigDecimal scoreTotal = BigDecimal.ZERO;
        BigDecimal onTimeTotal = BigDecimal.ZERO;
        for (SupplierScore score : scores) {
            scoreTotal = scoreTotal.add(score.getAvgScore() == null ? BigDecimal.ZERO : score.getAvgScore());
            onTimeTotal = onTimeTotal.add(score.getOnTimeRate() == null ? BigDecimal.ZERO : score.getOnTimeRate());
        }
        result.put("openPurchaseOrders", openPo);
        result.put("delayedAsns", delayedOpen);
        result.put("asnOnTimeRate", ratio(BigDecimal.valueOf(onTime), closed, 4));
        result.put("supplierOnTimeRate", scores.isEmpty()
                ? ratio(BigDecimal.valueOf(onTime), closed, 4)
                : ratio(onTimeTotal, scores.size(), 4));
        result.put("supplierAvgScore", ratio(scoreTotal, scores.size(), 2));
        return result;
    }

    static BigDecimal ratio(BigDecimal numerator, long denominator, int scale) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private static String camel(String metric) {
        StringBuilder sb = new StringBuilder();
        for (String part : metric.toLowerCase().split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
