package com.ir.sandbox;

import com.ir.forecast.ForecastEngine;
import com.ir.snapshot.InventorySnapshot;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class SandboxEngine {
    @lombok.Data public static class Result {private BigDecimal totalCost=BigDecimal.ZERO;private Map<String,BigDecimal> costByType=new LinkedHashMap<>(),costByWarehouse=new LinkedHashMap<>(),costByCarrier=new LinkedHashMap<>();private BigDecimal serviceLevel=BigDecimal.ZERO;private BigDecimal stockoutUnits=BigDecimal.ZERO;private BigDecimal avgLeadDays=BigDecimal.ZERO;private List<Map<String,Object>> dailySeries=new ArrayList<>();private List<Map<String,Object>> perSkuSummary=new ArrayList<>();}
    private final ForecastEngine forecast;
    public SandboxEngine(){this.forecast=new ForecastEngine();}
    public SandboxEngine(ForecastEngine forecast){this.forecast=forecast;}
    public Result run(ScenarioParams p,BaselineData b){
        Result r=new Result();BigDecimal demand=BigDecimal.ZERO,fulfilled=BigDecimal.ZERO,stockout=BigDecimal.ZERO;Map<String,BigDecimal> stock=new LinkedHashMap<>();
        for(InventorySnapshot x:b.getInventory()){String key=x.getWarehouseCode()+"/"+x.getSku();stock.put(key,x.getQtyAvailable().multiply(p.getInitialInventoryMultiplier()));}
        int days=Math.max(1,p.getHorizonDays());for(Map.Entry<String,List<BigDecimal>>e:b.getDemandBySku().entrySet()){List<BigDecimal> f=forecast.seasonalNaive(e.getValue(),days);BigDecimal skuDemand=BigDecimal.ZERO,skuFulfilled=BigDecimal.ZERO;for(BigDecimal x:f)skuDemand=skuDemand.add(x.multiply(p.getDemandMultiplier()));for(int d=0;d<days;d++){BigDecimal q=f.get(d).multiply(p.getDemandMultiplier());demand=demand.add(q);String wh=chooseWarehouse(p,b,e.getKey(),stock);BigDecimal have=stock.getOrDefault(wh+"/"+e.getKey(),BigDecimal.ZERO);BigDecimal got=have.min(q).max(BigDecimal.ZERO);stock.put(wh+"/"+e.getKey(),have.subtract(got));skuFulfilled=skuFulfilled.add(got);fulfilled=fulfilled.add(got);stockout=stockout.add(q.subtract(got));BigDecimal cost=p.getHandlingCostPerOrder().add(p.getPackagingCostPerOrder()).add(carrierCost(p,q));r.getDailySeries().add(day(d,q,got,cost));r.setTotalCost(r.getTotalCost().add(cost));}Map<String,Object> row=new LinkedHashMap<>();row.put("sku",e.getKey());row.put("demand",skuDemand);row.put("fulfilled",skuFulfilled);row.put("stockout",skuDemand.subtract(skuFulfilled));r.getPerSkuSummary().add(row);}
        BigDecimal storage=BigDecimal.ZERO;for(Map.Entry<String,BigDecimal>x:stock.entrySet()){storage=storage.add(x.getValue().multiply(p.getStorageCostPerUnitDay()).multiply(BigDecimal.valueOf(days)));String wh=x.getKey().split("/")[0];add(r.getCostByWarehouse(),wh,storage);}
        r.getCostByType().put("HANDLING",p.getHandlingCostPerOrder().multiply(BigDecimal.valueOf(Math.max(1,demand.intValue()))));r.getCostByType().put("PACKAGING",p.getPackagingCostPerOrder().multiply(BigDecimal.valueOf(Math.max(1,demand.intValue()))));r.getCostByType().put("STORAGE",storage);BigDecimal penalty=stockout.multiply(p.getStockoutPenaltyPerUnit());r.getCostByType().put("STOCKOUT_PENALTY",penalty);r.setTotalCost(r.getTotalCost().add(storage).add(penalty));r.setStockoutUnits(stockout);r.setServiceLevel(demand.signum()==0?BigDecimal.ONE:fulfilled.divide(demand,6,RoundingMode.HALF_UP));r.setAvgLeadDays(BigDecimal.valueOf(p.getAllocationStrategy().startsWith("SINGLE")?2.4:1.8));return r;
    }
    private String chooseWarehouse(ScenarioParams p,BaselineData b,String sku,Map<String,BigDecimal>stock){if("SINGLE_WAREHOUSE".equals(p.getAllocationStrategy()))return p.getSingleWarehouse()==null?"WH-SH":p.getSingleWarehouse();if("LOWEST_COST".equals(p.getAllocationStrategy())){String best="WH-SH";BigDecimal v=null;for(String wh:Arrays.asList("WH-SH","WH-BJ","WH-GZ")){BigDecimal x=stock.getOrDefault(wh+"/"+sku,BigDecimal.ZERO);if(v==null||x.compareTo(v)>0){v=x;best=wh;}}return best;}if("BALANCED".equals(p.getAllocationStrategy()))return "WH-BJ";String x=b.getSkuWarehouse().get(sku);return x==null?"WH-SH":x;}
    private BigDecimal carrierCost(ScenarioParams p,BigDecimal q){String carrier=p.getCarrierMix().isEmpty()?"SELF":p.getCarrierMix().keySet().iterator().next();return q.multiply(p.getCarrierRate().getOrDefault(carrier,BigDecimal.valueOf(2)));}
    private Map<String,Object> day(int d,BigDecimal demand,BigDecimal f,BigDecimal cost){Map<String,Object>m=new LinkedHashMap<>();m.put("date",LocalDate.now().plusDays(d+1));m.put("demand",demand);m.put("fulfilled",f);m.put("cost",cost);return m;}
    private void add(Map<String,BigDecimal>m,String k,BigDecimal v){m.put(k,m.getOrDefault(k,BigDecimal.ZERO).add(v));}
}
