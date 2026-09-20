package com.ir.balance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {
    private final BalanceEngine engine;

    public BalanceController(BalanceEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(engine.overview());
    }

    @PostMapping("/run")
    public R<Map<String, Object>> run() {
        CtBalanceRun run = engine.run("MANUAL");
        return R.ok(engine.runDetail(run.getId()));
    }

    @GetMapping("/run/page")
    public R<Page<CtBalanceRun>> runs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(engine.runs(current, size));
    }

    @GetMapping("/run/{id}")
    public R<Map<String, Object>> run(@PathVariable Long id) {
        return R.ok(engine.runDetail(id));
    }

    @GetMapping("/decision/page")
    public R<Page<CtBalanceDecision>> decisions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategy,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(engine.decisions(status, strategy, current, size));
    }

    @PostMapping("/decision/{id}/approve")
    public R<CtBalanceDecision> approve(@PathVariable Long id) {
        return R.ok(engine.approve(id));
    }

    @PostMapping("/decision/{id}/reject")
    public R<CtBalanceDecision> reject(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Object reason = (body == null ? Collections.emptyMap() : body).get("reason");
        return R.ok(engine.reject(id, reason == null ? null : String.valueOf(reason)));
    }

    @GetMapping("/config")
    public R<BalanceConfig> config() {
        return R.ok(engine.config());
    }

    @PostMapping("/config")
    public R<BalanceConfig> config(@RequestBody Map<String, Object> patch) {
        return R.ok(engine.updateConfig(patch));
    }
}
