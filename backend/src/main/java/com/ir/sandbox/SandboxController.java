package com.ir.sandbox;

import com.ir.action.CtAction;
import com.ir.alert.AlertEngine;
import com.ir.common.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {
    private final SandboxService service;
    private final AutoSandboxService autoSandbox;
    private final BalancePolicy policy;
    private final AlertEngine alerts;

    public SandboxController(
            SandboxService service,
            AutoSandboxService autoSandbox,
            BalancePolicy policy,
            AlertEngine alerts) {
        this.service = service;
        this.autoSandbox = autoSandbox;
        this.policy = policy;
        this.alerts = alerts;
    }

    @PostMapping("/baseline")
    public R<CtScenario> baseline() {
        return R.ok(service.baseline());
    }

    @PostMapping("/scenario")
    public R<CtScenario> create(@RequestBody Map<String, Object> request) {
        ScenarioParams params = toParams(request.get("params"));
        String name = request.get("name") == null
                ? "未命名场景" : String.valueOf(request.get("name"));
        return R.ok(service.create(name, params));
    }

    @PostMapping("/scenario/{id}/run")
    public R<CtScenario> run(@PathVariable Long id) {
        return R.ok(service.run(id));
    }

    @GetMapping("/scenario/page")
    public R<Page<CtScenario>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.page(name, status, kind, current, size));
    }

    @GetMapping("/scenario/{id}")
    public R<CtScenario> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @GetMapping("/defaults")
    public R<ScenarioParams> defaults() {
        return R.ok(new ScenarioParams());
    }

    @GetMapping("/compare")
    public R<List<Map<String, Object>>> compare(@RequestParam String ids) {
        return R.ok(service.compare(ids));
    }

    @PostMapping("/scenario/{id}/apply")
    public R<List<CtAction>> apply(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean execute) {
        return R.ok(service.apply(id, execute));
    }

    @PostMapping("/auto/run")
    public R<Map<String, Object>> autoRun() {
        return R.ok(autoSandbox.run());
    }

    @GetMapping("/auto/latest")
    public R<Map<String, Object>> autoLatest() {
        return R.ok(autoSandbox.latest());
    }

    @GetMapping("/policy")
    public R<Map<String, Object>> policy() {
        return R.ok(policy.snapshot());
    }

    @PutMapping("/policy")
    public R<Map<String, Object>> updatePolicy(@RequestBody Map<String, Object> request) {
        java.math.BigDecimal cost = decimal(request.get("costWeight"), policy.costWeight());
        java.math.BigDecimal efficiency = decimal(
                request.get("efficiencyWeight"), policy.efficiencyWeight());
        Map<String, Object> snapshot = policy.update(cost, efficiency);
        if (truthy(request.get("reevaluate"))) {
            snapshot.put("alerts", alerts.evaluate().size());
        }
        return R.ok(snapshot);
    }

    private java.math.BigDecimal decimal(Object value, java.math.BigDecimal fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value)
                || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private ScenarioParams toParams(Object value) {
        if (!(value instanceof Map)) {
            return new ScenarioParams();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .convertValue(value, ScenarioParams.class);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("场景参数格式错误", ex);
        }
    }
}
