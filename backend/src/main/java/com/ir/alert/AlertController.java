package com.ir.alert;

import com.ir.action.CtAction;
import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertController {
    private final AlertEngine engine;

    public AlertController(AlertEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/page")
    public R<List<CtAlert>> page(@RequestParam(required = false) String status) {
        return R.ok(engine.page(status));
    }

    @PostMapping("/evaluate")
    public R<List<CtAlert>> evaluate() {
        return R.ok(engine.evaluate());
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        List<CtAlert> alerts = engine.page(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", alerts.size());
        result.put("open", count(alerts, "OPEN"));
        result.put("acked", count(alerts, "ACKED"));
        result.put("resolved", count(alerts, "RESOLVED"));
        result.put("ignored", count(alerts, "IGNORED"));
        return R.ok(result);
    }

    @PostMapping("/{id}/ack")
    public R<CtAlert> ack(@PathVariable Long id) {
        return R.ok(engine.update(id, "ACKED"));
    }

    @PostMapping("/{id}/resolve")
    public R<CtAlert> resolve(@PathVariable Long id) {
        return R.ok(engine.update(id, "RESOLVED"));
    }

    @PostMapping("/{id}/ignore")
    public R<CtAlert> ignore(@PathVariable Long id) {
        return R.ok(engine.update(id, "IGNORED"));
    }

    @PostMapping("/{id}/execute-suggested")
    public R<CtAction> executeSuggested(@PathVariable Long id) {
        return R.ok(engine.executeSuggested(id));
    }

    private int count(List<CtAlert> alerts, String status) {
        int count = 0;
        for (CtAlert alert : alerts) {
            if (status.equals(alert.getStatus())) {
                count++;
            }
        }
        return count;
    }
}
