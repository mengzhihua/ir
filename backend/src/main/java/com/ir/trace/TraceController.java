package com.ir.trace;

import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trace")
public class TraceController {
    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    @GetMapping("/page")
    public R<List<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) Boolean stuck) {
        return R.ok(service.page(
                keyword, status, warehouseCode, carrierCode, stuck));
    }

    @GetMapping("/{orderNo}")
    public R<Map<String, Object>> detail(@PathVariable String orderNo) {
        return R.ok(service.detail(orderNo));
    }
}
