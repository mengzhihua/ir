package com.ir.cost;

import com.ir.common.R;
import com.ir.snapshot.CostRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cost")
public class CostController {
    private final CostService service;

    public CostController(CostService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(service.summary(days));
    }

    @GetMapping("/page")
    public R<List<CostRecord>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String costType,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return R.ok(service.page(
                orderNo, costType, warehouseCode, carrierCode, from, to));
    }

    @GetMapping("/saving")
    public R<Map<String, Object>> saving() {
        return R.ok(service.saving());
    }

    @GetMapping("/target")
    public R<List<CtCostTarget>> target() {
        return R.ok(service.targets());
    }

    @PostMapping("/target")
    public R<CtCostTarget> target(@RequestBody CtCostTarget target) {
        return R.ok(service.saveTarget(target));
    }
}
