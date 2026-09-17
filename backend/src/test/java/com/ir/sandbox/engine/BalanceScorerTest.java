package com.ir.sandbox.engine;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceScorerTest {
    @Test
    void cheaperScenarioGetsHigherCostScore() {
        BalanceScorer.Card cheap = card(100, 0.9, 2, 0);
        BalanceScorer.Card expensive = card(200, 0.9, 2, 0);
        BalanceScorer.score(Arrays.asList(cheap, expensive),
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5));
        assertTrue(cheap.getCostScore().compareTo(expensive.getCostScore()) > 0);
        assertEquals(0, cheap.getEfficiencyScore().compareTo(expensive.getEfficiencyScore()));
        assertTrue(cheap.getBalanceScore().compareTo(expensive.getBalanceScore()) > 0);
    }

    @Test
    void fasterAndFullerServiceGetsHigherEfficiency() {
        BalanceScorer.Card efficient = card(150, 0.99, 1, 0);
        BalanceScorer.Card slow = card(150, 0.70, 5, 40);
        BalanceScorer.score(Arrays.asList(efficient, slow),
                BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.8));
        assertTrue(efficient.getEfficiencyScore().compareTo(slow.getEfficiencyScore()) > 0);
        assertTrue(efficient.getBalanceScore().compareTo(slow.getBalanceScore()) > 0);
    }

    @Test
    void equalCardsScoreOne() {
        BalanceScorer.Card a = card(80, 1, 2, 0);
        BalanceScorer.Card b = card(80, 1, 2, 0);
        BalanceScorer.score(Arrays.asList(a, b), BigDecimal.ONE, BigDecimal.ONE);
        assertEquals(new BigDecimal("1.0000"), a.getCostScore());
        assertEquals(new BigDecimal("1.0000"), a.getEfficiencyScore());
        assertEquals(new BigDecimal("1.0000"), a.getBalanceScore());
    }

    private BalanceScorer.Card card(
            double cost, double service, double lead, double stockout) {
        BalanceScorer.Card card = new BalanceScorer.Card();
        card.setTotalCost(BigDecimal.valueOf(cost));
        card.setServiceLevel(BigDecimal.valueOf(service));
        card.setAvgLeadDays(BigDecimal.valueOf(lead));
        card.setStockoutUnits(BigDecimal.valueOf(stockout));
        return card;
    }
}
