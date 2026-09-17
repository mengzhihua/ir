package com.ir.forecast;

import com.ir.action.CtAction;
import com.ir.common.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @GetMapping("/history")
    public R<List<BigDecimal>> history(
            @RequestParam String sku,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(defaultValue = "90") int days) {
        return R.ok(service.history(sku, warehouseCode, days));
    }

    @PostMapping("/run")
    public R<Map<String, Object>> run(@RequestBody Map<String, Object> request) {
        String sku = String.valueOf(request.get("sku"));
        String warehouse = request.get("warehouseCode") == null
                ? null
                : String.valueOf(request.get("warehouseCode"));
        int horizon = request.get("horizon") == null
                ? 14
                : Integer.parseInt(String.valueOf(request.get("horizon")));
        String method = request.get("method") == null
                ? "AUTO"
                : String.valueOf(request.get("method"));
        return R.ok(service.run(sku, warehouse, horizon, method));
    }

    @GetMapping("/replenish")
    public R<List<Map<String, Object>>> replenish(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(defaultValue = "14") int horizon,
            @RequestParam(defaultValue = "3") int serviceDays) {
        return R.ok(service.replenish(warehouseCode, horizon, serviceDays));
    }

    @PostMapping("/replenish/to-action")
    @SuppressWarnings("unchecked")
    public R<List<CtAction>> toAction(@RequestBody Object body) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (body instanceof List) {
            for (Object item : (List<?>) body) {
                if (item instanceof Map) {
                    rows.add((Map<String, Object>) item);
                }
            }
        } else if (body instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) body;
            if (map.get("rows") instanceof List) {
                for (Object item : (List<?>) map.get("rows")) {
                    if (item instanceof Map) {
                        rows.add((Map<String, Object>) item);
                    }
                }
            } else {
                rows.add(map);
            }
        }
        return R.ok(service.toActions(rows));
    }

    @GetMapping("/page")
    public R<Page<CtForecast>> page(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.page(sku, warehouseCode, method, current, size));
    }
}
