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
}
