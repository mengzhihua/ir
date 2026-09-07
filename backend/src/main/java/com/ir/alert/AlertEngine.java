package com.ir.alert;

import com.ir.action.ActionService;
import com.ir.snapshot.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AlertEngine {
    private final DataStore store;
    private final List<Map<String,Object>> alerts = new CopyOnWriteArrayList<>();
    private final ActionService actions;
    public AlertEngine(DataStore store, ActionService actions) { this.store=store;this.actions=actions; }
    public synchronized List<Map<String,Object>> evaluate() {
        LocalDateTime now=LocalDateTime.now();
        for(OrderSnapshot o:store.orders) if("AUDITED".equals(o.getStatus()) && o.getOrderTime()!=null && Duration.between(o.getOrderTime(),now).toHours()>4) add("ORDER_AUDITED_STUCK","ORDER_STUCK","HIGH","ORDER",o.getOrderNo(),o.getWarehouseCode(),"订单审核卡单","AUDITED 超过 4 小时","OMS_HOLD");
        for(WmsOrderSnapshot w:store.wmsOrders) { OrderSnapshot o=store.order(w.getExternalNo()); if("PICKING".equals(w.getStatus()) && o!=null && o.getOrderTime()!=null && Duration.between(o.getOrderTime(),now).toHours()>6) add("WMS_PICKING_STUCK","WMS_STUCK","HIGH","ORDER",o.getOrderNo(),o.getWarehouseCode(),"WMS 拣货卡单","PICKING 超过 6 小时","WMS_ALLOCATE"); }
        for(ShipmentSnapshot s:store.shipments) if(s.getPlannedArriveTime()!=null && s.getPlannedArriveTime().isBefore(now) && !"DELIVERED".equals(s.getStatus()) && !"CLOSED".equals(s.getStatus())) add("TMS_DELAY","TMS_DELAY","HIGH","WAYBILL",s.getWaybillCode(),"","运输到达延迟","计划到达时间已过","TMS_SYNC_TRACK");
        for(InventorySnapshot x:store.inventory) if(x.getQtyAvailable().compareTo(x.getSafetyQty())<0) add("LOW_STOCK","LOW_STOCK","MEDIUM","SKU",x.getSku(),x.getWarehouseCode(),"低库存","可用库存低于安全库存","WMS_REPLENISH");
        return new ArrayList<>(alerts);
    }
    private void add(String rule,String type,String severity,String targetType,String key,String wh,String title,String detail,String suggested) {
        for(Map<String,Object> a:alerts) if(rule.equals(a.get("ruleCode")) && key.equals(a.get("targetKey")) && "OPEN".equals(a.get("status"))) return;
        Map<String,Object> a=new LinkedHashMap<>(); a.put("id",(long)alerts.size()+1); a.put("alertNo","ALERT"+String.format("%06d",alerts.size()+1)); a.put("ruleCode",rule);a.put("type",type);a.put("severity",severity);a.put("targetType",targetType);a.put("targetKey",key);a.put("warehouseCode",wh);a.put("title",title);a.put("detail",detail);a.put("status","OPEN");a.put("suggestedAction",suggested);a.put("createdAt",LocalDateTime.now()); alerts.add(a);
    }
    public List<Map<String,Object>> page(String status) { if(status==null)return new ArrayList<>(alerts); List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object>a:alerts)if(status.equals(a.get("status")))out.add(a);return out; }
    public Map<String,Object> get(long id) { for(Map<String,Object>a:alerts)if(((Number)a.get("id")).longValue()==id)return a;return null; }
    public Map<String,Object> update(long id,String status) { Map<String,Object>a=get(id);if(a==null)return null;a.put("status",status);if("RESOLVED".equals(status))a.put("resolvedAt",LocalDateTime.now());return a; }
    public Map<String,Object> executeSuggested(long id) { Map<String,Object>a=get(id);if(a==null)return null; Map<String,Object> p=new LinkedHashMap<>();p.put("type",a.get("suggestedAction"));p.put("targetKey",a.get("targetKey"));p.put("alertId",id);Map<String,Object> r=actions.createAndExecute(p);a.put("actionId",r.get("id"));return r; }
}
