package com.ir.sandbox.engine;

import org.junit.jupiter.api.Test;
import com.ir.snapshot.entity.InventorySnapshot;
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
    @Test void cheaperCarrierLowersCostAndSlowsLead(){
        SandboxEngine e=new SandboxEngine();
        ScenarioParams sf=new ScenarioParams();
        sf.setHorizonDays(7);
        sf.setCarrierMix(Collections.singletonMap("SF",BigDecimal.ONE));
        ScenarioParams self=new ScenarioParams();
        self.setHorizonDays(7);
        self.setCarrierMix(Collections.singletonMap("SELF01",BigDecimal.ONE));
        SandboxEngine.Result fast=e.run(sf,data());
        SandboxEngine.Result cheap=e.run(self,data());
        assertTrue(cheap.getTotalCost().compareTo(fast.getTotalCost())<0);
        assertTrue(cheap.getAvgLeadDays().compareTo(fast.getAvgLeadDays())>0);
        assertTrue(fast.getCostByCarrier().containsKey("SF"));
        assertTrue(cheap.getCostByCarrier().containsKey("SELF01"));
    }
    @Test void legacyJdlMixNormalizesToJd(){
        ScenarioParams a=new ScenarioParams();
        a.setCarrierMix(Collections.singletonMap("JDL",BigDecimal.ONE));
        ScenarioParams n=a.normalized();
        assertTrue(n.getCarrierMix().containsKey("JD"));
        assertEquals(new BigDecimal("1"), n.getCarrierMix().get("JD"));
    }

    @Test void oneHundredMillionCapitalIsReliable(){
        ScenarioParams a=new ScenarioParams();
        a.setHorizonDays(30);
        a.setWorkingCapital(new BigDecimal("100000000"));
        SandboxEngine.Result result=new SandboxEngine().run(a,data());
        assertEquals("RELIABLE", result.getCapitalVerdict());
        assertTrue(result.getCapitalFeasible());
        assertTrue(result.getCapitalUtilization().compareTo(new BigDecimal("0.10")) < 0);
        assertEquals(0, result.getDeferredPurchaseQty().signum());
        assertEquals(30, result.getDailySeries().size());
        assertEquals(1, result.getSkuCount());
    }

    @Test void tightCapitalDefersReplenishment(){
        ScenarioParams tight=new ScenarioParams();
        tight.setHorizonDays(7);
        tight.setInitialInventoryMultiplier(BigDecimal.ZERO);
        tight.setWorkingCapital(BigDecimal.TEN);
        tight.setPurchaseCostPerUnit(BigDecimal.valueOf(50));
        SandboxEngine.Result result=new SandboxEngine().run(tight,data());
        assertEquals("INSUFFICIENT", result.getCapitalVerdict());
        assertTrue(result.getDeferredPurchaseQty().signum() > 0);
        ScenarioParams rich=new ScenarioParams();
        rich.setHorizonDays(7);
        rich.setInitialInventoryMultiplier(BigDecimal.ZERO);
        rich.setWorkingCapital(new BigDecimal("100000000"));
        assertTrue(new SandboxEngine().run(rich,data()).getStockoutUnits()
                .compareTo(result.getStockoutUnits()) <= 0);
    }
    @Test void sameDayAndNextDayReplenishmentIncreaseStock(){
        ScenarioParams immediate=new ScenarioParams();
        immediate.setHorizonDays(3);
        immediate.setInitialInventoryMultiplier(BigDecimal.ZERO);
        immediate.setReplenishLeadDays(0);
        ScenarioParams nextDay=new ScenarioParams();
        nextDay.setHorizonDays(3);
        nextDay.setInitialInventoryMultiplier(BigDecimal.ZERO);
        nextDay.setReplenishLeadDays(1);
        ScenarioParams never = new ScenarioParams();
        never.setHorizonDays(3);
        never.setInitialInventoryMultiplier(BigDecimal.ZERO);
        never.setReplenishLeadDays(999);
        SandboxEngine e=new SandboxEngine();
        BigDecimal immediateStockout = e.run(immediate, data()).getStockoutUnits();
        BigDecimal nextDayStockout = e.run(nextDay, data()).getStockoutUnits();
        BigDecimal neverStockout = e.run(never, data()).getStockoutUnits();
        assertTrue(immediateStockout.compareTo(neverStockout) < 0);
        assertTrue(nextDayStockout.compareTo(neverStockout) < 0);
    }
}
