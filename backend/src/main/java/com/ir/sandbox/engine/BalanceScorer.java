package com.ir.sandbox.engine;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 在一批沙盘结果上给成本分、效率分和综合分，便于兼顾成本与时效。 */

public final class BalanceScorer {
    private BalanceScorer() {
    }

    @Data
    public static class Card {
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal serviceLevel = BigDecimal.ZERO;
        private BigDecimal avgLeadDays = BigDecimal.ZERO;
        private BigDecimal stockoutUnits = BigDecimal.ZERO;
        private BigDecimal costScore = BigDecimal.ZERO;
        private BigDecimal efficiencyScore = BigDecimal.ZERO;
        private BigDecimal balanceScore = BigDecimal.ZERO;
    }

    public static void score(
            List<Card> cards,
            BigDecimal costWeight,
            BigDecimal efficiencyWeight) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        BigDecimal costW = weight(costWeight);
        BigDecimal effW = weight(efficiencyWeight);
        BigDecimal sum = costW.add(effW);
        if (sum.signum() == 0) {
            costW = BigDecimal.valueOf(0.5);
            effW = BigDecimal.valueOf(0.5);
            sum = BigDecimal.ONE;
        }
        costW = costW.divide(sum, 6, RoundingMode.HALF_UP);
        effW = effW.divide(sum, 6, RoundingMode.HALF_UP);

        BigDecimal minCost = min(cards, true, false, false);
        BigDecimal maxCost = max(cards, true, false, false);
        BigDecimal minLead = min(cards, false, true, false);
        BigDecimal maxLead = max(cards, false, true, false);
        BigDecimal minStock = min(cards, false, false, true);
        BigDecimal maxStock = max(cards, false, false, true);

        for (Card card : cards) {
            BigDecimal costScore = invert(card.getTotalCost(), minCost, maxCost);
            BigDecimal leadScore = invert(card.getAvgLeadDays(), minLead, maxLead);
            BigDecimal stockScore = invert(card.getStockoutUnits(), minStock, maxStock);
            BigDecimal service = nz(card.getServiceLevel());
            if (service.compareTo(BigDecimal.ONE) > 0) {
                service = BigDecimal.ONE;
            }
            BigDecimal efficiency = service.multiply(BigDecimal.valueOf(0.5))
                    .add(leadScore.multiply(BigDecimal.valueOf(0.3)))
                    .add(stockScore.multiply(BigDecimal.valueOf(0.2)));
            card.setCostScore(round(costScore));
            card.setEfficiencyScore(round(efficiency));
            card.setBalanceScore(round(costScore.multiply(costW).add(efficiency.multiply(effW))));
        }
    }

    private static BigDecimal invert(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal current = nz(value);
        if (max.subtract(min).signum() == 0) {
            return BigDecimal.ONE;
        }
        return max.subtract(current).divide(max.subtract(min), 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private static BigDecimal min(List<Card> cards, boolean cost, boolean lead, boolean stock) {
        BigDecimal best = null;
        for (Card card : cards) {
            BigDecimal value = pick(card, cost, lead, stock);
            if (best == null || value.compareTo(best) < 0) {
                best = value;
            }
        }
        return best == null ? BigDecimal.ZERO : best;
    }

    private static BigDecimal max(List<Card> cards, boolean cost, boolean lead, boolean stock) {
        BigDecimal best = null;
        for (Card card : cards) {
            BigDecimal value = pick(card, cost, lead, stock);
            if (best == null || value.compareTo(best) > 0) {
                best = value;
            }
        }
        return best == null ? BigDecimal.ZERO : best;
    }

    private static BigDecimal pick(Card card, boolean cost, boolean lead, boolean stock) {
        if (cost) {
            return nz(card.getTotalCost());
        }
        if (lead) {
            return nz(card.getAvgLeadDays());
        }
        return nz(card.getStockoutUnits());
    }

    private static BigDecimal weight(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.valueOf(0.5) : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
