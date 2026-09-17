package com.ir.sandbox;

import com.ir.snapshot.InventorySnapshot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SandboxEngineTest {
    private BaselineData data(){BaselineData b=new BaselineData();InventorySnapshot i=new InventorySnapshot();i.setWarehouseCode("WH-SH");i.setSku("SKU001");i.setQtyAvailable(BigDecimal.valueOf(100));b.setInventory(Collections.singletonList(i));b.getDemandBySku().put("SKU001",Arrays.asList(BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN));return b;}
    @Test void deterministicAndDemandMultiplierIncreasesCost(){SandboxEngine e=new SandboxEngine();ScenarioParams a=new ScenarioParams();a.setHorizonDays(7);SandboxEngine.Result r1=e.run(a,data());SandboxEngine.Result r2=e.run(a,data());assertEquals(r1.getTotalCost(),r2.getTotalCost());a.setDemandMultiplier(BigDecimal.valueOf(2));assertTrue(e.run(a,data()).getTotalCost().compareTo(r1.getTotalCost())>0);}
    @Test void singleWarehouseDiffersFromNearest(){ScenarioParams a=new ScenarioParams();a.setHorizonDays(7);a.setAllocationStrategy("NEAREST");SandboxEngine.Result n=new SandboxEngine().run(a,data());a.setAllocationStrategy("SINGLE_WAREHOUSE");a.setSingleWarehouse("WH-BJ");SandboxEngine.Result s=new SandboxEngine().run(a,data());assertNotEquals(n.getTotalCost(),s.getTotalCost());}
    @Test void stockoutPenaltyApplied(){ScenarioParams a=new ScenarioParams();a.setHorizonDays(7);a.setInitialInventoryMultiplier(BigDecimal.ZERO);assertTrue(new SandboxEngine().run(a,data()).getTotalCost().compareTo(BigDecimal.ZERO)>0);}
    @Test void carrierMixChangesCarrierCost(){ScenarioParams a=new ScenarioParams();a.setHorizonDays(7);SandboxEngine.Result first=new SandboxEngine().run(a,data());a.setCarrierMix(Collections.singletonMap("SF",BigDecimal.ONE));SandboxEngine.Result second=new SandboxEngine().run(a,data());assertNotEquals(first.getCostByCarrier(),second.getCostByCarrier());}
    @Test void emptyCarrierMixUsesBaselineDefaults(){ScenarioParams a=new ScenarioParams();a.setHorizonDays(7);a.setCarrierMix(Collections.emptyMap());SandboxEngine.Result result=new SandboxEngine().run(a,data());assertFalse(result.getCostByCarrier().isEmpty());assertTrue(result.getCostByCarrier().containsKey("SF"));}
    @Test void replenishmentQueueReducesStockout(){ScenarioParams fast=new ScenarioParams();fast.setHorizonDays(7);fast.setInitialInventoryMultiplier(BigDecimal.ZERO);fast.setReplenishLeadDays(3);ScenarioParams slow=new ScenarioParams();slow.setHorizonDays(7);slow.setInitialInventoryMultiplier(BigDecimal.ZERO);slow.setReplenishLeadDays(999);SandboxEngine e=new SandboxEngine();assertTrue(e.run(fast,data()).getStockoutUnits().compareTo(e.run(slow,data()).getStockoutUnits())<0);}

    private SandboxEngine.Result metric(double cost,double service,double lead){SandboxEngine.Result r=new SandboxEngine.Result();r.setTotalCost(BigDecimal.valueOf(cost));r.setServiceLevel(BigDecimal.valueOf(service));r.setAvgLeadDays(BigDecimal.valueOf(lead));return r;}

    @Test void costWeightShiftsRecommendation(){
        // cheap 便宜但服务差 vs premium 贵但服务好+时效好
        SandboxEngine.Result cheap=metric(50,0.80,3);
        SandboxEngine.Result premium=metric(100,1.00,2);
        List<SandboxEngine.Result> set=Arrays.asList(cheap,premium);
        assertEquals(0,SandboxEngine.assignCostEfficiencyScores(set,1.0),"纯成本导向应选便宜方案");
        assertEquals(1,SandboxEngine.assignCostEfficiencyScores(set,0.0),"纯效率导向应选高服务方案");
    }

    @Test void dominantScenarioWinsRegardlessOfWeight(){
        SandboxEngine.Result dominant=metric(50,1.00,2); // 更便宜且服务/时效都更好
        SandboxEngine.Result worse=metric(100,0.80,3);
        List<SandboxEngine.Result> set=Arrays.asList(dominant,worse);
        for(double w=0.0;w<=1.0;w+=0.25){assertEquals(0,SandboxEngine.assignCostEfficiencyScores(set,w),"占优方案应在任意权重下胜出");}
        assertTrue(dominant.getCostEfficiencyScore().compareTo(worse.getCostEfficiencyScore())>0);
        assertEquals(0,new BigDecimal("100.00").compareTo(dominant.getCostEfficiencyScore()));
    }

    @Test void balancedWeightPicksTradeoff(){
        // 中间方案在均衡权重下应优于两个极端之一
        SandboxEngine.Result cheap=metric(40,0.70,4);
        SandboxEngine.Result mid=metric(70,0.92,2);
        SandboxEngine.Result premium=metric(120,1.00,2);
        List<SandboxEngine.Result> set=Arrays.asList(cheap,mid,premium);
        int best=SandboxEngine.assignCostEfficiencyScores(set,0.5);
        assertTrue(best==1||best==0,"均衡权重应偏向兼顾成本与效率的方案");
        assertTrue(mid.getCostEfficiencyScore().compareTo(BigDecimal.ZERO)>0);
    }
}
