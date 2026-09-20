package com.ir.objective;

import com.ir.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/objective")
public class ObjectiveController {
    private final ObjectiveService service;
    private final MetricService metrics;

    public ObjectiveController(ObjectiveService service, MetricService metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @GetMapping
    public R<List<CtObjective>> list() {
        return R.ok(service.list());
    }

    @PostMapping
    public R<CtObjective> save(@RequestBody CtObjective objective) {
        return R.ok(service.save(objective));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @GetMapping("/scoreboard")
    public R<Map<String, Object>> scoreboard() {
        return R.ok(service.scoreboard());
    }

    @GetMapping("/metrics")
    public R<Map<String, Object>> metrics() {
        return R.ok(metrics.metrics());
    }
}
