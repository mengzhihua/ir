package com.ir.sandbox;

import com.ir.common.CarrierCodes;
import com.ir.common.WarehouseCodes;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.ShipmentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    @Value("${ir.sandbox.cost-weight:0.5}")
    private BigDecimal costWeight;

    @Value("${ir.sandbox.efficiency-weight:0.5}")
    private BigDecimal efficiencyWeight;

    public BalanceAdvisor() {
    }

    public BalanceAdvisor(BigDecimal costWeight, BigDecimal efficiencyWeight) {
        this.costWeight = costWeight;
        this.efficiencyWeight = efficiencyWeight;
    }

    public Advice adviseDelay(ShipmentSnapshot shipment) {
        if (shipment == null || shipment.getWaybillCode() == null) {
            return null;
        }
        String current = CarrierCodes.toTms(shipment.getCarrierCode());
        String best = pickCarrier(current, candidates(null), costWeight, efficiencyWeight);
        String type = SYNC;
        String carrier = current;
        if (best != null && !best.equals(current)) {
            boolean costFirst = nz(costWeight).compareTo(nz(efficiencyWeight)) > 0;
            boolean effFirst = nz(efficiencyWeight).compareTo(nz(costWeight)) > 0;
            if (costFirst && cheaper(best, current)) {
                type = SWITCH;
                carrier = best;
            } else if (effFirst && faster(best, current)) {
                type = SWITCH;
                carrier = best;
            } else if (!costFirst && !effFirst) {
                type = SWITCH;
                carrier = best;
            }
        }
        return advice(type, shipment.getWaybillCode(), carrier);
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
            result.add(advice(SWITCH, shipment.getWaybillCode(), target));
            limit++;
        }
        return result;
    }

    public String pickCarrier(String current) {
        return pickCarrier(current, candidates(null), costWeight, efficiencyWeight);
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
        boolean costFirst = nz(costWeight).compareTo(nz(efficiencyWeight)) > 0;
        boolean effFirst = nz(efficiencyWeight).compareTo(nz(costWeight)) > 0;
        if (costFirst) {
            return CarrierCodes.SELF01;
        }
        if (effFirst) {
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

    private Advice advice(String type, String waybill, String carrier) {
        Advice advice = new Advice();
        advice.type = type;
        advice.targetKey = waybill;
        advice.carrierCode = carrier;
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

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.valueOf(0.5) : value;
    }

    public static class Advice {
        private String type;
        private String targetKey;
        private String carrierCode;

        public String getType() {
            return type;
        }

        public String getTargetKey() {
            return targetKey;
        }

        public String getCarrierCode() {
            return carrierCode;
        }

        public Map<String, Object> params() {
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            if (carrierCode != null) {
                params.put("carrierCode", carrierCode);
                params.put("waybillCode", targetKey);
            }
            return params;
        }
    }
}
