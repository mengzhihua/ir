package com.ir.sandbox.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.alert.service.AlertEngine;
import com.ir.common.R;
import com.ir.sandbox.engine.ScenarioParams;
import com.ir.sandbox.entity.CtScenario;
import com.ir.sandbox.service.AutoSandboxService;
import com.ir.sandbox.service.BalancePolicy;
import com.ir.sandbox.service.SandboxService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {
    private final SandboxService service;
    private final AutoSandboxService autoSandbox;
    private final BalancePolicy policy;
    private final AlertEngine alerts;
    private final ActionService actions;

    public SandboxController(
            SandboxService service,
            AutoSandboxService autoSandbox,
            BalancePolicy policy,
            AlertEngine alerts,
            ActionService actions) {
        this.service = service;
        this.autoSandbox = autoSandbox;
        this.policy = policy;
        this.alerts = alerts;
        this.actions = actions;
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
        ScenarioParams params = service.manualDefaults();
        params.setCostWeight(policy.costWeight());
        params.setEfficiencyWeight(policy.efficiencyWeight());
        return R.ok(params);
    }

    @GetMapping("/capital/tiers")
    public R<Map<String, Object>> capitalTiers() {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        row.put("presets", com.ir.sandbox.engine.CapitalTiers.presets());
        row.put("maxSku", com.ir.sandbox.engine.CapitalTiers.MAX_SKU);
        row.put("maxQty", com.ir.sandbox.engine.CapitalTiers.MAX_QTY);
        row.put("maxAmount", com.ir.sandbox.engine.CapitalTiers.MAX_AMOUNT);
        return R.ok(row);
    }

    @PostMapping("/capital")
    public R<Map<String, Object>> capital(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(service.analyzeCapital(
                workingCapital(request),
                intOrNull(request == null ? null : request.get("skuCount")),
                optionalDecimal(request == null ? null : request.get("inventoryQty"))));
    }

    @PostMapping("/capital/sweep")
    public R<Map<String, Object>> capitalSweep(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(service.sweepCapital(
                extraAmounts(request),
                intOrNull(request == null ? null : request.get("skuCount")),
                optionalDecimal(request == null ? null : request.get("inventoryQty"))));
    }

    @PostMapping("/capital/adopt")
    public R<CtScenario> adoptCapital(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(service.adoptRecommended(workingCapital(request)));
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

    @GetMapping("/auto/history")
    public R<List<Map<String, Object>>> autoHistory(
            @RequestParam(defaultValue = "8") int size) {
        return R.ok(autoSandbox.history(size));
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
        Integer safety = intOrNull(request.get("safetyDays"));
        Integer lead = intOrNull(request.get("replenishLeadDays"));
        Map<String, Object> snapshot = policy.update(cost, efficiency, safety, lead);
        snapshot.put("superseded", actions.supersedeOpposing(policy.stance()));
        if (truthy(request.get("reevaluate"))) {
            alerts.evaluate();
            snapshot.put("alerts", alerts.openCount());
            snapshot.put("openAlerts", alerts.openCount());
            snapshot.put("forecastStockoutAlerts", alerts.openCount("FORECAST_STOCKOUT"));
        }
        return R.ok(snapshot);
    }

    private java.math.BigDecimal workingCapital(Map<String, Object> request) {
        Object raw = request == null ? null : request.get("workingCapital");
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return new java.math.BigDecimal("100000000");
        }
        return new java.math.BigDecimal(String.valueOf(raw));
    }

    private java.math.BigDecimal optionalDecimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }

    private java.util.List<java.math.BigDecimal> extraAmounts(Map<String, Object> request) {
        java.util.List<java.math.BigDecimal> extras = new java.util.ArrayList<java.math.BigDecimal>();
        if (request == null) {
            return extras;
        }
        Object custom = request.get("customAmount");
        if (custom != null && !String.valueOf(custom).trim().isEmpty()) {
            extras.add(new java.math.BigDecimal(String.valueOf(custom)));
        }
        Object list = request.get("amounts");
        if (list instanceof java.util.List) {
            for (Object item : (java.util.List<?>) list) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    extras.add(new java.math.BigDecimal(String.valueOf(item)));
                }
            }
        }
        return extras;
    }

    private java.math.BigDecimal decimal(Object value, java.math.BigDecimal fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }

    private Integer intOrNull(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return new java.math.BigDecimal(String.valueOf(value)).intValue();
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
