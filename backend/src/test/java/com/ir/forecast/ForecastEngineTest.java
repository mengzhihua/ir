package com.ir.forecast;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastEngineTest {
    private final ForecastEngine engine=new ForecastEngine();
    private List<BigDecimal> weekly(){List<BigDecimal>x=new ArrayList<>();for(int i=0;i<56;i++)x.add(BigDecimal.valueOf((i%7)+1));return x;}
    @Test void eachMethodReturnsHorizonPoints(){List<BigDecimal>x=weekly();assertEquals(14,engine.movingAverage(x,14).size());assertEquals(14,engine.ses(x,14).size());assertEquals(14,engine.holt(x,14).size());assertEquals(14,engine.seasonalNaive(x,14).size());}
    @Test void autoPicksLowestMape(){ForecastEngine.Result r=engine.forecast(weekly(),14,"AUTO");assertEquals("SEASONAL_NAIVE",r.getMethod());}
    @Test void seasonalNaiveReproducesWeeklyPattern(){List<BigDecimal>r=engine.seasonalNaive(weekly(),7);assertEquals(0,BigDecimal.ONE.compareTo(r.get(0)));assertEquals(0,BigDecimal.valueOf(7).compareTo(r.get(6)));}
}
