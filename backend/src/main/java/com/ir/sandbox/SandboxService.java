package com.ir.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.DataStore;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SandboxService {
    private final DataStore store;private final SandboxEngine engine;private final CodeGenerator codes;private final ObjectMapper mapper;private final ActionService actions;private final List<Map<String,Object>> scenarios=new ArrayList<>();
    public SandboxService(DataStore store,SandboxEngine engine,CodeGenerator codes,ObjectMapper mapper,ActionService actions){this.store=store;this.engine=engine;this.codes=codes;this.mapper=mapper;this.actions=actions;}
    public synchronized Map<String,Object> baseline(){for(Map<String,Object>x:scenarios)if(Boolean.TRUE.equals(x.get("baseline")))return x;return createAndRun("基线场景",new ScenarioParams(),true);}
    public synchronized void ensureBaseline(){if(scenarios.isEmpty())baseline();}
    public Map<String,Object> createAndRun(String name,ScenarioParams p,boolean baseline){BaselineData b=BaselineData.from(store);SandboxEngine.Result result=engine.run(p,b);Map<String,Object>m=new LinkedHashMap<>();m.put("id",(long)scenarios.size()+1);m.put("scenarioNo",codes.next("SC"));m.put("name",name);m.put("baseline",baseline);m.put("params",p);m.put("result",result);m.put("status","RUN");m.put("totalCost",result.getTotalCost());m.put("serviceLevel",result.getServiceLevel());m.put("createdAt",LocalDateTime.now());scenarios.add(m);return m;}
    public Map<String,Object> create(String name,ScenarioParams p){return createAndRun(name,p,false);}
    public Map<String,Object> get(long id){for(Map<String,Object>x:scenarios)if(((Number)x.get("id")).longValue()==id)return x;return null;}
    public List<Map<String,Object>> page(){return new ArrayList<>(scenarios);}
    public List<Map<String,Object>> compare(String ids){List<Map<String,Object>>out=new ArrayList<>();for(String id:ids.split(",")){try{Map<String,Object>x=get(Long.parseLong(id.trim()));if(x!=null)out.add(x);}catch(Exception ignored){}}return out;}
    public List<Map<String,Object>> apply(long id){Map<String,Object>x=get(id);if(x==null)return Collections.emptyList();List<Map<String,Object>>out=new ArrayList<>();ScenarioParams p=(ScenarioParams)x.get("params");if("SINGLE_WAREHOUSE".equals(p.getAllocationStrategy()))for(String sku:Arrays.asList("SKU001","SKU002","SKU003","SKU004","SKU005")){Map<String,Object>q=new LinkedHashMap<>();q.put("type","OMS_REROUTE_WAREHOUSE");q.put("targetKey",sku);Map<String,Object>pa=new LinkedHashMap<>();pa.put("warehouseCode",p.getSingleWarehouse());q.put("params",pa);out.add(actions.createAndExecute(q));}return out;}
}
