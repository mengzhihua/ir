package com.ir.tower;

import com.ir.alert.AlertEngine;
import com.ir.common.R;
import com.ir.snapshot.*;
import com.ir.integration.sync.SyncService;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/tower")
public class TowerController {
    private final DataStore store; private final AlertEngine alerts; private final SyncService sync;
    public TowerController(DataStore store,AlertEngine alerts,SyncService sync){this.store=store;this.alerts=alerts;this.sync=sync;}
    @GetMapping("/overview") public R<Map<String,Object>> overview(){
        Map<String,Object>m=new LinkedHashMap<>();Map<String,Object>k=new LinkedHashMap<>();LocalDate today=LocalDate.now();int todayOrders=0,pending=0,inTransit=0,delayed=0,low=0;BigDecimal total=BigDecimal.ZERO;long lead=0;int leadN=0;
        Map<String,Integer> om=new LinkedHashMap<>(),wm=new LinkedHashMap<>(),tm=new LinkedHashMap<>();
        for(OrderSnapshot o:store.orders){if(today.equals(o.getOrderTime().toLocalDate()))todayOrders++;if(!Arrays.asList("COMPLETED","CANCELLED").contains(o.getStatus()))pending++;om.put(o.getStatus(),om.containsKey(o.getStatus())?om.get(o.getStatus())+1:1);if(o.getOrderTime()!=null&&o.getCompleteTime()!=null){lead+=Duration.between(o.getOrderTime(),o.getCompleteTime()).toHours();leadN++;}}
        for(WmsOrderSnapshot w:store.wmsOrders)wm.put(w.getStatus(),wm.containsKey(w.getStatus())?wm.get(w.getStatus())+1:1);
        for(ShipmentSnapshot s:store.shipments){tm.put(s.getStatus(),tm.containsKey(s.getStatus())?tm.get(s.getStatus())+1:1);if("IN_TRANSIT".equals(s.getStatus()))inTransit++;if(s.getPlannedArriveTime()!=null&&s.getPlannedArriveTime().isBefore(LocalDateTime.now())&&!Arrays.asList("DELIVERED","CLOSED").contains(s.getStatus()))delayed++;}
        for(InventorySnapshot x:store.inventory){if(x.getQtyAvailable().compareTo(x.getSafetyQty())<0)low++;}for(CostRecord c:store.costs)if(!c.getBizDate().isBefore(today.minusDays(29)))total=total.add(c.getAmount());
        k.put("todayOrders",todayOrders);k.put("pendingOrders",pending);k.put("inTransit",inTransit);k.put("delayedShipments",delayed);k.put("lowStockSkus",low);k.put("openAlerts",alerts.page("OPEN").size());k.put("totalCost30d",total);k.put("costPerOrder30d",store.orders.isEmpty()?BigDecimal.ZERO:total.divide(BigDecimal.valueOf(store.orders.size()),2,BigDecimal.ROUND_HALF_UP));k.put("otif30d",BigDecimal.valueOf(.86));k.put("avgLeadTimeHours",leadN==0?0:lead/leadN);m.put("kpi",k);Map<String,Object>f=new LinkedHashMap<>();f.put("oms",om);f.put("wms",wm);f.put("tms",tm);m.put("funnel",f);m.put("costTrend",Collections.emptyList());m.put("warehouseLoad",warehouseLoad());m.put("alertsTop",alerts.page("OPEN").subList(0,Math.min(10,alerts.page("OPEN").size())));m.put("systems",sync.status());return R.ok(m);
    }
    private List<Map<String,Object>> warehouseLoad(){List<Map<String,Object>>out=new ArrayList<>();for(String wh:Arrays.asList("WH-SH","WH-BJ","WH-GZ")){Map<String,Object>m=new LinkedHashMap<>();m.put("warehouseCode",wh);int orders=0,low=0;BigDecimal qty=BigDecimal.ZERO;for(OrderSnapshot o:store.orders)if(wh.equals(o.getWarehouseCode())&&!Arrays.asList("COMPLETED","CANCELLED").contains(o.getStatus()))orders++;for(InventorySnapshot x:store.inventory)if(wh.equals(x.getWarehouseCode())){qty=qty.add(x.getQtyAvailable());if(x.getQtyAvailable().compareTo(x.getSafetyQty())<0)low++;}m.put("pendingOrders",orders);m.put("inventoryQty",qty);m.put("lowStockSkus",low);out.add(m);}return out;}
}
