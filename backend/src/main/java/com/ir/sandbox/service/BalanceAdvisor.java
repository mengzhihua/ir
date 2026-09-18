package com.ir.sandbox.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ir.common.CarrierCodes;
import com.ir.common.WarehouseCodes;
import com.ir.sandbox.engine.BalanceScorer;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把沙盘的成本/效率权重落到可执行的 TMS 指令：延误追轨迹还是换商，超标换哪家。
 */

@Component
public class BalanceAdvisor {
    public static final String SWITCH = "TMS_SWITCH_CARRIER";
    public static final String SYNC = "TMS_SYNC_TRACK";

    private final BalancePolicy policy;
    private final BigDecimal costWeight;
    private final BigDecimal efficiencyWeight;

    @Autowired
    public BalanceAdvisor(BalancePolicy policy) {
        this.policy = policy;
        this.costWeight = null;
        this.efficiencyWeight = null;
    }

    public BalanceAdvisor(BigDecimal costWeight, BigDecimal efficiencyWeight) {
        this.policy = null;
        this.costWeight = costWeight;
        this.efficiencyWeight = efficiencyWeight;
    }

    public Advice adviseDelay(ShipmentSnapshot shipment) {
        if (shipment == null || shipment.getWaybillCode() == null) {
            return null;
        }
        String current = CarrierCodes.toTms(shipment.getCarrierCode());
        String best = pickCarrier(current, candidates(null), costW(), effW());
        String type = SYNC;
        String carrier = current;
        if (best != null && !best.equals(current)) {
            if (costFirst() && cheaper(best, current)) {
                type = SWITCH;
                carrier = best;
            } else if (efficiencyFirst() && faster(best, current)) {
                type = SWITCH;
                carrier = best;
            } else if (!costFirst() && !efficiencyFirst()) {
                type = SWITCH;
                carrier = best;
            }
        }
        return advice(type, shipment.getWaybillCode(), carrier, shipment);
    }

    public List<Advice> adviseOverrun(
            String warehouse,
            List<ShipmentSnapshot> shipments,
            List<OrderSnapshot> orders) {
        List<ShipmentSnapshot> open = matchWarehouse(warehouse, shipments, orders);
        List<Advice> result = switches(open);
        if (result.isEmpty()) {
            result = switches(openShipments(shipments));
        }
        return result;
    }

    private List<Advice> switches(List<ShipmentSnapshot> open) {
        List<Advice> result = new ArrayList<Advice>();
        int limit = 0;
        for (ShipmentSnapshot shipment : open) {
            if (limit >= 8) {
                break;
            }
            String current = CarrierCodes.toTms(shipment.getCarrierCode());
            String target = pickCheaperForOverrun(current);
            if (target == null || target.equals(current)) {
                continue;
            }
            result.add(advice(SWITCH, shipment.getWaybillCode(), target, shipment));
            limit++;
        }
        return result;
    }

    public Advice adviseStuckOrder(OrderSnapshot order, String ruleCode) {
        if (order == null || order.getOrderNo() == null) {
            return null;
        }
        String type;
        if ("AUDITED".equals(order.getStatus()) || "OMS_STUCK".equals(ruleCode)) {
            if (costFirst()) {
                type = "OMS_HOLD";
            } else if (efficiencyFirst()) {
                type = "OMS_AUTO_PROCESS";
            } else {
                type = "OMS_PRIORITIZE";
            }
        } else if (costFirst()) {
            type = "OMS_HOLD";
        } else {
            type = "OMS_PRIORITIZE";
        }
        Advice advice = new Advice();
        advice.type = type;
        advice.targetKey = order.getOrderNo();
        advice.warehouseCode = order.getWarehouseCode();
        if ("OMS_PRIORITIZE".equals(type)) {
            advice.priority = 10;
            advice.remark = "IR 成本/效率权衡后加急";
        }
        if ("OMS_HOLD".equals(type)) {
            advice.remark = "IR 成本优先，卡单先挂起";
        }
        return advice;
    }

    public List<Advice> adviseStockout(String sku, String warehouse, BigDecimal gap) {
        List<Advice> result = new ArrayList<Advice>();
        if (sku == null || sku.trim().isEmpty()) {
            return result;
        }
        BigDecimal qty = scaleQty(gap);
        Advice purchase = new Advice();
        purchase.type = "SRM_PURCHASE_SUGGEST";
        purchase.targetKey = sku.trim();
        purchase.sku = sku.trim();
        purchase.warehouseCode = warehouse;
        purchase.qty = qty;
        result.add(purchase);
        if (!costFirst()) {
            Advice replenish = new Advice();
            replenish.type = "WMS_REPLENISH";
            replenish.targetKey = warehouse == null ? "WH-SH" : warehouse;
            replenish.warehouseCode = replenish.targetKey;
            result.add(replenish);
        }
        return result;
    }

    public Advice adviseWmsStuck(WmsOrderSnapshot outbound, OrderSnapshot order) {
        if (outbound == null || outbound.getCode() == null) {
            return null;
        }
        Advice advice = new Advice();
        if (costFirst()) {
            advice.type = "OMS_HOLD";
            advice.targetKey = order != null && order.getOrderNo() != null
                    ? order.getOrderNo() : outbound.getExternalNo();
            advice.warehouseCode = outbound.getWarehouseCode();
            advice.remark = "IR 成本优先，仓内卡单先挂起";
        } else {
            advice.type = "WMS_ALLOCATE";
            advice.targetKey = outbound.getCode();
            advice.warehouseCode = outbound.getWarehouseCode();
        }
        return advice;
    }

    public static boolean opposes(String stance, String type, String carrier) {
        if (type == null || stance == null) {
            return false;
        }
        String mapped = CarrierCodes.toTms(carrier);
        if ("COST".equals(stance)) {
            if ("OMS_PRIORITIZE".equals(type) || "OMS_AUTO_PROCESS".equals(type)
                    || "WMS_ALLOCATE".equals(type)) {
                return true;
            }
            return "TMS_SWITCH_CARRIER".equals(type) && CarrierCodes.SF.equals(mapped);
        }
        if ("EFFICIENCY".equals(stance)) {
            if ("OMS_HOLD".equals(type)) {
                return true;
            }
            return "TMS_SWITCH_CARRIER".equals(type) && CarrierCodes.SELF01.equals(mapped);
        }
        return false;
    }

    public boolean costFirst() {
        return nz(costW()).compareTo(nz(effW())) > 0;
    }

    public boolean efficiencyFirst() {
        return nz(effW()).compareTo(nz(costW())) > 0;
    }

    public String pickCarrier(String current) {
        return pickCarrier(current, candidates(null), costW(), effW());
    }

    public String pickCheaperForOverrun(String current) {
        String from = CarrierCodes.toTms(current);
        List<String> cheaper = new ArrayList<String>();
        for (String carrier : CarrierCodes.TMS) {
            if (cheaper(carrier, from)) {
                cheaper.add(carrier);
            }
        }
        if (cheaper.isEmpty()) {
            return from;
        }
        if (costFirst()) {
            return CarrierCodes.SELF01;
        }
        if (efficiencyFirst()) {
            return CarrierCodes.SF.equals(from) ? CarrierCodes.JD : from;
        }
        String step = CarrierCodes.oneStepCheaper(from);
        return step == null ? from : step;
    }

    String pickCarrier(
            String current,
            List<String> options,
            BigDecimal costW,
            BigDecimal effW) {
        List<String> carriers = options == null || options.isEmpty()
                ? candidates(null) : options;
        List<BalanceScorer.Card> cards = new ArrayList<BalanceScorer.Card>();
        for (String carrier : carriers) {
            cards.add(card(carrier));
        }
        BalanceScorer.score(cards, costW, effW);
        String best = CarrierCodes.toTms(current);
        BigDecimal bestScore = null;
        for (int i = 0; i < carriers.size(); i++) {
            BigDecimal score = cards.get(i).getBalanceScore();
            if (bestScore == null || score.compareTo(bestScore) > 0) {
                best = carriers.get(i);
                bestScore = score;
            }
        }
        return best;
    }

    private List<ShipmentSnapshot> matchWarehouse(
            String warehouse,
            List<ShipmentSnapshot> shipments,
            List<OrderSnapshot> orders) {
        List<ShipmentSnapshot> result = new ArrayList<ShipmentSnapshot>();
        Map<String, String> warehouseByOrder = new LinkedHashMap<String, String>();
        if (orders != null) {
            for (OrderSnapshot order : orders) {
                if (order.getOrderNo() != null) {
                    warehouseByOrder.put(order.getOrderNo(), order.getWarehouseCode());
                }
            }
        }
        for (ShipmentSnapshot shipment : openShipments(shipments)) {
            if (warehouse == null || warehouse.trim().isEmpty()
                    || "UNKNOWN".equals(warehouse)) {
                result.add(shipment);
                continue;
            }
            String site = WarehouseCodes.toOms(shipment.getFromSiteCode());
            if (warehouse.equals(site) || warehouse.equals(shipment.getFromSiteCode())) {
                result.add(shipment);
                continue;
            }
            String orderWarehouse = warehouseByOrder.get(shipment.getSourceNo());
            if (warehouse.equals(orderWarehouse)
                    || warehouse.equals(WarehouseCodes.toOms(orderWarehouse))) {
                result.add(shipment);
            }
        }
        return result;
    }

    private List<ShipmentSnapshot> openShipments(List<ShipmentSnapshot> shipments) {
        List<ShipmentSnapshot> result = new ArrayList<ShipmentSnapshot>();
        if (shipments == null) {
            return result;
        }
        for (ShipmentSnapshot shipment : shipments) {
            if (shipment == null || shipment.getWaybillCode() == null) {
                continue;
            }
            if (Arrays.asList("DELIVERED", "CLOSED", "CANCELLED")
                    .contains(shipment.getStatus())) {
                continue;
            }
            result.add(shipment);
        }
        return result;
    }

    private BigDecimal scaleQty(BigDecimal gap) {
        BigDecimal base = gap == null || gap.signum() <= 0 ? BigDecimal.TEN : gap;
        if (costFirst()) {
            return base.multiply(BigDecimal.valueOf(1.5)).setScale(0, java.math.RoundingMode.UP);
        }
        if (efficiencyFirst()) {
            return base.setScale(0, java.math.RoundingMode.UP);
        }
        return base.multiply(BigDecimal.valueOf(1.2)).setScale(0, java.math.RoundingMode.UP);
    }

    public static BigDecimal freightSaving(
            String fromCarrier,
            String toCarrier,
            BigDecimal freight) {
        BigDecimal from = CarrierCodes.rate(fromCarrier);
        BigDecimal to = CarrierCodes.rate(toCarrier);
        if (from.signum() <= 0 || to.compareTo(from) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal next = CarrierCodes.scaledFreight(fromCarrier, toCarrier, freight);
        if (freight == null || next == null) {
            return BigDecimal.ZERO;
        }
        return freight.subtract(next).max(BigDecimal.ZERO);
    }

    public static BigDecimal shareSaving(BigDecimal total, int count) {
        if (total == null || total.signum() <= 0 || count <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private Advice advice(String type, String waybill, String carrier, ShipmentSnapshot shipment) {
        Advice advice = new Advice();
        advice.type = type;
        advice.targetKey = waybill;
        advice.carrierCode = carrier;
        if (SWITCH.equals(type)) {
            advice.expectedSaving = freightSaving(
                    shipment == null ? null : shipment.getCarrierCode(),
                    carrier,
                    shipment == null ? null : shipment.getFreightAmount());
        }
        return advice;
    }

    private BalanceScorer.Card card(String carrier) {
        BalanceScorer.Card card = new BalanceScorer.Card();
        card.setTotalCost(CarrierCodes.rate(carrier));
        card.setServiceLevel(BigDecimal.ONE);
        card.setAvgLeadDays(CarrierCodes.lead(carrier));
        card.setStockoutUnits(BigDecimal.ZERO);
        return card;
    }

    private List<String> candidates(List<String> options) {
        return options == null || options.isEmpty()
                ? new ArrayList<String>(CarrierCodes.TMS) : options;
    }

    private boolean cheaper(String left, String right) {
        return CarrierCodes.rate(left).compareTo(CarrierCodes.rate(right)) < 0;
    }

    private boolean faster(String left, String right) {
        return CarrierCodes.lead(left).compareTo(CarrierCodes.lead(right)) < 0;
    }

    private BigDecimal costW() {
        return policy != null ? policy.costWeight() : costWeight;
    }

    private BigDecimal effW() {
        return policy != null ? policy.efficiencyWeight() : efficiencyWeight;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.valueOf(0.5) : value;
    }

    public static class Advice {
        private String type;
        private String targetKey;
        private String carrierCode;
        private String sku;
        private String warehouseCode;
        private String remark;
        private Integer priority;
        private BigDecimal qty;
        private BigDecimal expectedSaving;

        public String getType() {
            return type;
        }

        public String getTargetKey() {
            return targetKey;
        }

        public String getCarrierCode() {
            return carrierCode;
        }

        public BigDecimal getExpectedSaving() {
            return expectedSaving;
        }

        public Map<String, Object> params() {
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            if (carrierCode != null) {
                params.put("carrierCode", carrierCode);
                params.put("waybillCode", targetKey);
            }
            if (sku != null) {
                params.put("sku", sku);
            }
            if (warehouseCode != null) {
                params.put("warehouseCode", warehouseCode);
            }
            if (qty != null) {
                params.put("qty", qty);
                params.put("suggestQty", qty);
            }
            if (priority != null) {
                params.put("priority", priority);
            }
            if (remark != null) {
                params.put("remark", remark);
                params.put("reason", remark);
            }
            return params;
        }
    }
}
