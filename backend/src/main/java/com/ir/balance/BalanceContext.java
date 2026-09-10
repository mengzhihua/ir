package com.ir.balance;

import com.ir.snapshot.CostRecord;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.ShipmentSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class BalanceContext {
    private LocalDateTime now;
    private Map<String, Object> metrics;
    /** objectiveCode -> attainment(0..1). */
    private Map<String, BigDecimal> attainment;
    /** objectiveCode -> weight. */
    private Map<String, BigDecimal> weight;
    private List<InventorySnapshot> inventory;
    private List<OrderSnapshot> orders;
    private List<ShipmentSnapshot> shipments;
    private List<PurchaseSnapshot> purchases;
    private List<CostRecord> freightCosts;
    private BalanceConfig config;

    public BigDecimal attainment(String objectiveCode) {
        return attainment.getOrDefault(objectiveCode, BigDecimal.ONE);
    }

    public boolean serviceGuarded() {
        BigDecimal guard = config.getServiceGuardAttainment();
        return attainment("NPS").compareTo(guard) < 0 || attainment("OTIF").compareTo(guard) < 0;
    }

    /** 目标差距越大、权重越高,优先级越高. */
    public double priority(String... objectiveCodes) {
        double score = 0;
        for (String code : objectiveCodes) {
            BigDecimal gap = BigDecimal.ONE.subtract(attainment(code)).max(BigDecimal.valueOf(0.05));
            score += gap.doubleValue() * weight.getOrDefault(code, BigDecimal.TEN).doubleValue();
        }
        return score;
    }
}
