package com.ir.sandbox;

import com.ir.action.CtAction;
import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public SandboxController(SandboxService service) {
        this.service = service;
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
    public R<List<CtScenario>> page() {
        return R.ok(service.page());
    }

    @GetMapping("/scenario/{id}")
    public R<CtScenario> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @GetMapping("/compare")
    public R<List<Map<String, Object>>> compare(@RequestParam String ids) {
        return R.ok(service.compare(ids));
    }

    @PostMapping("/scenario/{id}/apply")
    public R<List<CtAction>> apply(@PathVariable Long id) {
        return R.ok(service.apply(id));
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
