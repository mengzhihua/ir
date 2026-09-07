package com.ir.integration;

import com.ir.common.R;
import com.ir.integration.sync.SyncService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final SyncService sync;
    public IntegrationController(SyncService sync) { this.sync=sync; }
    @GetMapping("/system") public R<List<Map<String,Object>>> systems() {
        List<Map<String,Object>> out=new ArrayList<>(); for(String c:Arrays.asList("OMS","TMS","WMS","BMS","SRM")) {Map<String,Object> m=new LinkedHashMap<>();m.put("code",c);m.put("mode","MOCK");m.put("enabled",!"SRM".equals(c));out.add(m);} return R.ok(out);
    }
    @PutMapping("/system/{id}") public R<Map<String,Object>> update(@PathVariable Long id,@RequestBody Map<String,Object> body){body.put("id",id);return R.ok(body);}
    @PostMapping("/system/{code}/health") public R<Map<String,Object>> health(@PathVariable String code){return R.ok(sync.health(code));}
    @PostMapping("/sync") public R<Map<String,Object>> sync(@RequestBody(required=false) Map<String,Object> body){return R.ok(sync.syncAll());}
    @PostMapping("/sync/{code}") public R<Map<String,Object>> one(@PathVariable String code){return R.ok(sync.sync(code));}
    @GetMapping("/sync-log/page") public R<Map<String,Object>> logs(){return R.ok(sync.status());}
}
