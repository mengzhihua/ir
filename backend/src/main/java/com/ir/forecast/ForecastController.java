package com.ir.forecast;

import com.ir.action.ActionService;
import com.ir.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
    private final ForecastService service; private final ActionService actions;
    public ForecastController(ForecastService service,ActionService actions){this.service=service;this.actions=actions;}
    @GetMapping("/history") public R<List<java.math.BigDecimal>> history(@RequestParam String sku,@RequestParam(required=false)String warehouseCode,@RequestParam(defaultValue="90")int days){return R.ok(service.history(sku,warehouseCode,days));}
    @PostMapping("/run") public R<Map<String,Object>> run(@RequestBody Map<String,Object> req){return R.ok(service.run(String.valueOf(req.get("sku")),req.get("warehouseCode")==null?null:String.valueOf(req.get("warehouseCode")),req.get("horizon")==null?14:Integer.parseInt(String.valueOf(req.get("horizon"))),req.get("method")==null?"AUTO":String.valueOf(req.get("method"))));}
    @GetMapping("/replenish") public R<List<Map<String,Object>>> replenish(@RequestParam(required=false)String warehouseCode,@RequestParam(defaultValue="14")int horizon,@RequestParam(defaultValue="3")int serviceDays){return R.ok(service.replenish(warehouseCode,horizon,serviceDays));}
    @PostMapping("/replenish/to-action") public R<List<Map<String,Object>>> toAction(@RequestBody List<Map<String,Object>> rows){List<Map<String,Object>>out=new ArrayList<>();for(Map<String,Object>x:rows){Map<String,Object>q=new LinkedHashMap<>();q.put("type","SRM_PURCHASE_SUGGEST");q.put("targetKey",x.get("sku"));q.put("params",x);out.add(actions.createAndExecute(q));}return R.ok(out);}
    @GetMapping("/page") public R<List<Map<String,Object>>> page(){return R.ok(service.page());}
}
