package com.ir.action;

import com.ir.integration.client.*;
import com.ir.snapshot.DataStore;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ActionService {
    private final ClientFactory clients; private final DataStore store; private final List<Map<String,Object>> actions=new CopyOnWriteArrayList<>();
    public ActionService(ClientFactory clients,DataStore store){this.clients=clients;this.store=store;}
    public Map<String,Object> createAndExecute(Map<String,Object> req) {
        String type=String.valueOf(req.get("type")); String target=String.valueOf(req.get("targetKey"));
        Map<String,Object> a=new LinkedHashMap<>(); long id=actions.size()+1;a.put("id",id);a.put("actionNo","ACT"+String.format("%06d",id));a.put("type",type);a.put("targetKey",target);a.put("targetSystem",system(type));a.put("params",req.get("params"));a.put("alertId",req.get("alertId"));a.put("operator","admin");a.put("createdAt",LocalDateTime.now());a.put("expectedSaving",BigDecimal.ZERO);
        ActionCommand c=new ActionCommand();c.setType(type);c.setTargetKey(target);c.setParams(req.get("params") instanceof Map?(Map<String,Object>)req.get("params"):new LinkedHashMap<>());
        if("SRM_PURCHASE_SUGGEST".equals(type) || "SRM".equals(system(type))){a.put("status","PENDING");a.put("result","SRM 未接入，已生成采购建议");}
        else { try { if("OMS".equals(system(type)))clients.oms("MOCK","").execute(c); else if("WMS".equals(system(type)))clients.wms("MOCK","").execute(c); else clients.tms("MOCK","").execute(c); a.put("status","SUCCESS");a.put("result","MOCK 执行成功"); } catch(Exception e){a.put("status","FAILED");a.put("result",e.getMessage());} }
        a.put("executedAt",LocalDateTime.now()); actions.add(a);return a;
    }
    private String system(String type){if(type.startsWith("OMS_"))return"OMS";if(type.startsWith("WMS_"))return"WMS";if(type.startsWith("TMS_"))return"TMS";return"SRM";}
    public List<Map<String,Object>> page(){return new ArrayList<>(actions);}
    public Map<String,Object> retry(long id){for(Map<String,Object>a:actions)if(((Number)a.get("id")).longValue()==id)return createAndExecute(a);return null;}
    public List<String> types(){return Arrays.asList("OMS_REROUTE_WAREHOUSE","OMS_HOLD","OMS_UNHOLD","OMS_PRIORITIZE","OMS_AUTO_PROCESS","OMS_CANCEL","WMS_ALLOCATE","WMS_REPLENISH","TMS_DISPATCH","TMS_SYNC_TRACK","TMS_SWITCH_CARRIER","SRM_PURCHASE_SUGGEST");}
}
