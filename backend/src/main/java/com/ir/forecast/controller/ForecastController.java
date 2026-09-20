package com.ir.forecast.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ir.action.entity.CtAction;
import com.ir.common.R;
import com.ir.forecast.entity.CtForecast;
import com.ir.forecast.service.ForecastService;
import com.ir.sandbox.service.BalancePolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
    private final ForecastService service;
    private final BalancePolicy policy;

    public ForecastController(ForecastService service, BalancePolicy policy) {
        this.service = service;
        this.policy = policy;
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
    public R<Page<Map<String, Object>>> replenish(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "14") int horizon,
            @RequestParam(required = false) Integer serviceDays,
            @RequestParam(required = false) Integer leadDays,
            @RequestParam(required = false) Boolean belowRop) {
        int days = serviceDays == null ? policy.safetyDays() : serviceDays;
        int lead = leadDays == null ? policy.replenishLeadDays() : leadDays;
        List<Map<String, Object>> rows = service.replenish(
                warehouseCode, sku, horizon, days, lead);
        if (Boolean.TRUE.equals(belowRop)) {
            List<Map<String, Object>> gaps = new java.util.ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("belowRop"))) {
                    gaps.add(row);
                }
            }
            rows = gaps;
        }
        long total = rows.size();
        long from = Math.max(0, (current - 1) * size);
        long to = Math.min(total, from + size);
        List<Map<String, Object>> pageRows = from >= to
                ? java.util.Collections.emptyList()
                : rows.subList((int) from, (int) to);
        Page<Map<String, Object>> page = new Page<>(current, size, total);
        page.setRecords(pageRows);
        return R.ok(page);
    }

    @PostMapping("/replenish/to-action")
    public R<List<CtAction>> toAction(
            @RequestBody Map<String, Object> request) {
        String type = request.get("type") == null
                ? "SRM_PURCHASE_SUGGEST"
                : String.valueOf(request.get("type"));
        String supplier = request.get("supplier") == null
                ? "" : String.valueOf(request.get("supplier"));
        Object value = request.get("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = value instanceof List
                ? (List<Map<String, Object>>) value : java.util.Collections.emptyList();
        for (Map<String, Object> row : rows) {
            row.put("type", type);
            row.put("supplier", supplier);
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
