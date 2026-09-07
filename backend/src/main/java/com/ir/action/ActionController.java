package com.ir.action;

import com.ir.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/action")
public class ActionController {
    private final ActionService service;
    public ActionController(ActionService service){this.service=service;}
    @PostMapping public R<Map<String,Object>> create(@RequestBody Map<String,Object> req){return R.ok(service.createAndExecute(req));}
    @PostMapping("/{id}/retry") public R<Map<String,Object>> retry(@PathVariable long id){return R.ok(service.retry(id));}
    @GetMapping("/page") public R<List<Map<String,Object>>> page(){return R.ok(service.page());}
    @GetMapping("/types") public R<List<String>> types(){return R.ok(service.types());}
}
