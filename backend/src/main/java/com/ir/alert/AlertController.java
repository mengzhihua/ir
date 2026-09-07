package com.ir.alert;

import com.ir.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/alert")
public class AlertController {
    private final AlertEngine engine;
    public AlertController(AlertEngine engine){this.engine=engine;}
    @GetMapping("/page") public R<List<Map<String,Object>>> page(@RequestParam(required=false)String status){return R.ok(engine.page(status));}
    @PostMapping("/evaluate") public R<List<Map<String,Object>>> evaluate(){return R.ok(engine.evaluate());}
    @GetMapping("/stats") public R<Map<String,Object>> stats(){List<Map<String,Object>> a=engine.page(null);Map<String,Object>m=new LinkedHashMap<>();m.put("total",a.size());for(String s:Arrays.asList("OPEN","ACKED","RESOLVED","IGNORED")){int n=0;for(Map<String,Object>x:a)if(s.equals(x.get("status")))n++;m.put(s.toLowerCase(),n);}return R.ok(m);}
    @PostMapping("/{id}/ack") public R<Map<String,Object>> ack(@PathVariable long id){return R.ok(engine.update(id,"ACKED"));}
    @PostMapping("/{id}/resolve") public R<Map<String,Object>> resolve(@PathVariable long id){return R.ok(engine.update(id,"RESOLVED"));}
    @PostMapping("/{id}/ignore") public R<Map<String,Object>> ignore(@PathVariable long id){return R.ok(engine.update(id,"IGNORED"));}
    @PostMapping("/{id}/execute-suggested") public R<Map<String,Object>> suggested(@PathVariable long id){return R.ok(engine.executeSuggested(id));}
}
