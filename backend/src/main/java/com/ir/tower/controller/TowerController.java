package com.ir.tower.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ir.common.R;
import com.ir.tower.service.TowerCommandService;
import com.ir.tower.service.TowerService;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/tower")
public class TowerController {
    private final TowerService service;
    private final TowerCommandService commands;

    public TowerController(TowerService service, TowerCommandService commands) {
        this.service = service;
        this.commands = commands;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(service.overview());
    }

    @PostMapping("/command")
    public R<Map<String, Object>> command(@RequestBody Map<String, Object> request) {
        return R.ok(commands.execute(request));
    }

    @PostMapping("/command/batch")
    public R<Map<String, Object>> commandBatch(
            @RequestBody(required = false) Map<String, Object> request) {
        return R.ok(commands.executeBatch(request == null ? Collections.emptyMap() : request));
    }
}
