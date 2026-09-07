package com.ir.sandbox;

import com.ir.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {
    private final SandboxService service;
    public SandboxController(SandboxService service){this.service=service;}
    @PostMapping("/baseline") public R<Map<String,Object>> baseline(){return R.ok(service.baseline());}
    @PostMapping("/scenario") public R<Map<String,Object>> create(@RequestBody Map<String,Object> req){ScenarioParams p=new ScenarioParams();Object params=req.get("params");if(params instanceof Map){Map<?,?>m=(Map<?,?>)params;if(m.get("demandMultiplier")!=null)p.setDemandMultiplier(new java.math.BigDecimal(String.valueOf(m.get("demandMultiplier"))));if(m.get("allocationStrategy")!=null)p.setAllocationStrategy(String.valueOf(m.get("allocationStrategy")));if(m.get("singleWarehouse")!=null)p.setSingleWarehouse(String.valueOf(m.get("singleWarehouse")));if(m.get("initialInventoryMultiplier")!=null)p.setInitialInventoryMultiplier(new java.math.BigDecimal(String.valueOf(m.get("initialInventoryMultiplier"))));}return R.ok(service.create(String.valueOf(req.get("name")==null?"未命名场景":req.get("name")),p));}
    @PostMapping("/scenario/{id}/run") public R<Map<String,Object>> run(@PathVariable long id){return R.ok(service.get(id));}
    @GetMapping("/scenario/page") public R<List<Map<String,Object>>> page(){return R.ok(service.page());}
    @GetMapping("/scenario/{id}") public R<Map<String,Object>> get(@PathVariable long id){return R.ok(service.get(id));}
    @GetMapping("/compare") public R<List<Map<String,Object>>> compare(@RequestParam String ids){return R.ok(service.compare(ids));}
    @PostMapping("/scenario/{id}/apply") public R<List<Map<String,Object>>> apply(@PathVariable long id){return R.ok(service.apply(id));}
}
