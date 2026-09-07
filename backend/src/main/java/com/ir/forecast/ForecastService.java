package com.ir.forecast;

import com.ir.snapshot.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ForecastService {
    private final DataStore store; private final ForecastEngine engine; private final List<Map<String,Object>> runs=new ArrayList<>();
    public ForecastService(DataStore store,ForecastEngine engine){this.store=store;this.engine=engine;}
    public List<BigDecimal> history(String sku,String wh,int days){Map<LocalDate,BigDecimal>m=new TreeMap<>();for(SalesPoint p:store.sales)if(sku.equals(p.getSku())&&(wh==null||wh.equals(p.getWarehouseCode()))&&!p.getSalesDate().isBefore(LocalDate.now().minusDays(days-1)))m.put(p.getSalesDate(),m.containsKey(p.getSalesDate())?m.get(p.getSalesDate()).add(p.getQty()):p.getQty());List<BigDecimal>out=new ArrayList<>();for(BigDecimal x:m.values())out.add(x);return out;}
    public Map<String,Object> run(String sku,String wh,int horizon,String method){ForecastEngine.Result r=engine.forecast(history(sku,wh,90),horizon,method);Map<String,Object>m=new LinkedHashMap<>();m.put("history",r.getHistory());m.put("forecast",r.getForecast());m.put("method",r.getMethod());m.put("mape",r.getMape());m.put("backtest",r.getBacktest());m.put("sku",sku);m.put("warehouseCode",wh);runs.add(m);return m;}
    public List<Map<String,Object>> replenish(String wh,int horizon,int serviceDays){List<Map<String,Object>>out=new ArrayList<>();for(String sku:Arrays.asList("SKU001","SKU002","SKU003","SKU004","SKU005"))for(InventorySnapshot x:store.inventory)if(sku.equals(x.getSku())&&(wh==null||wh.equals(x.getWarehouseCode()))){Map<String,Object>m=run(sku,x.getWarehouseCode(),horizon,"AUTO");BigDecimal demand=BigDecimal.ZERO;for(ForecastEngine.Point p:(List<ForecastEngine.Point>)m.get("forecast"))demand=demand.add(p.getQty());BigDecimal safety=demand.divide(BigDecimal.valueOf(Math.max(1,horizon)),6,BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(serviceDays));BigDecimal suggest=demand.add(safety).subtract(x.getQtyAvailable()).max(BigDecimal.ZERO);m=new LinkedHashMap<>(m);m.put("forecastDemand",demand);m.put("onHand",x.getQtyOnHand());m.put("available",x.getQtyAvailable());m.put("inTransit",BigDecimal.ZERO);m.put("safety",safety);m.put("suggestQty",suggest);m.put("stockoutDate",suggest.signum()>0?LocalDate.now().plusDays(Math.max(1,x.getQtyAvailable().divide(demand.divide(BigDecimal.valueOf(Math.max(1,horizon)),6,BigDecimal.ROUND_HALF_UP).max(BigDecimal.ONE),0,BigDecimal.ROUND_DOWN).longValue())):null);out.add(m);}return out;}
    public List<Map<String,Object>> page(){return new ArrayList<>(runs);}
}
