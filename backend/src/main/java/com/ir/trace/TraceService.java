package com.ir.trace;

import com.ir.alert.AlertEngine;
import com.ir.action.ActionService;
import com.ir.snapshot.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TraceService {
    private final DataStore store; private final AlertEngine alerts; private final ActionService actions;
    public TraceService(DataStore store,AlertEngine alerts,ActionService actions){this.store=store;this.alerts=alerts;this.actions=actions;}
    public List<Map<String,Object>> page(String keyword,String status,String warehouse,String carrier,Boolean stuck){
        List<Map<String,Object>> out=new ArrayList<>(); for(OrderSnapshot o:store.orders){
            if(keyword!=null&&!o.getOrderNo().contains(keyword))continue;if(status!=null&&!status.equals(o.getStatus()))continue;if(warehouse!=null&&!warehouse.equals(o.getWarehouseCode()))continue;if(carrier!=null&&!carrier.equals(o.getCarrierCode()))continue;
            WmsOrderSnapshot w=store.wms(o.getOrderNo());ShipmentSnapshot s=store.shipment(o.getOrderNo());long hours=stuckHours(o,w,s);if(Boolean.TRUE.equals(stuck)&&hours<=0)continue;
            Map<String,Object>m=row(o,w,s,hours);out.add(m);
        } return out;
    }
    public Map<String,Object> detail(String no){OrderSnapshot o=store.order(no);if(o==null)return null;WmsOrderSnapshot w=store.wms(no);ShipmentSnapshot s=store.shipment(no);Map<String,Object>m=row(o,w,s,stuckHours(o,w,s));List<Map<String,Object>>timeline=new ArrayList<>();timeline.add(node("OMS","ORDER",o.getOrderTime(),o.getStatus(),o.getOrderNo()));if(w!=null)timeline.add(node("WMS","OUTBOUND",o.getShipTime(),w.getStatus(),w.getCode()));if(s!=null)timeline.add(node("TMS","WAYBILL",s.getActualArriveTime()!=null?s.getActualArriveTime():s.getPlannedArriveTime(),s.getStatus(),s.getWaybillCode()));m.put("timeline",timeline);List<Map<String,Object>> aa=new ArrayList<>();for(Map<String,Object>a:alerts.page(null))if(no.equals(a.get("targetKey")))aa.add(a);m.put("alerts",aa);m.put("actions",actions.page());return m;}
    private Map<String,Object> row(OrderSnapshot o,WmsOrderSnapshot w,ShipmentSnapshot s,long hours){Map<String,Object>m=new LinkedHashMap<>();m.put("orderNo",o.getOrderNo());m.put("oms",o);m.put("wms",w);m.put("tms",s);m.put("stage",stage(o,w,s));m.put("stuckHours",hours);BigDecimal c=BigDecimal.ZERO;for(CostRecord x:store.costs)if(o.getOrderNo().equals(x.getOrderNo()))c=c.add(x.getAmount());m.put("costTotal",c);return m;}
    private String stage(OrderSnapshot o,WmsOrderSnapshot w,ShipmentSnapshot s){if("CANCELLED".equals(o.getStatus()))return"CANCELLED";if(s!=null&&("DELIVERED".equals(s.getStatus())||"CLOSED".equals(s.getStatus())))return"DELIVERED";if(s!=null)return"TRANSPORT";if(w!=null)return"WAREHOUSE";return"ORDER";}
    private long stuckHours(OrderSnapshot o,WmsOrderSnapshot w,ShipmentSnapshot s){if("AUDITED".equals(o.getStatus()))return Math.max(0,Duration.between(o.getOrderTime(),LocalDateTime.now()).toHours()-4);if(w!=null&&"PICKING".equals(w.getStatus()))return Math.max(0,Duration.between(o.getOrderTime(),LocalDateTime.now()).toHours()-6);if(s!=null&&s.getPlannedArriveTime()!=null&&s.getPlannedArriveTime().isBefore(LocalDateTime.now())&&!("DELIVERED".equals(s.getStatus())||"CLOSED".equals(s.getStatus())))return Math.max(1,Duration.between(s.getPlannedArriveTime(),LocalDateTime.now()).toHours());return 0;}
    private Map<String,Object> node(String system,String node,LocalDateTime time,String status,String detail){Map<String,Object>m=new LinkedHashMap<>();m.put("system",system);m.put("node",node);m.put("time",time);m.put("status",status);m.put("detail",detail);return m;}
}
