package com.ir.supply;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.common.R;
import com.ir.snapshot.PurchaseSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/supply")
public class SupplyController {
    private final SupplyService service;

    public SupplyController(SupplyService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(service.overview());
    }

    @GetMapping("/purchase/page")
    public R<Page<PurchaseSnapshot>> page(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.page(docType, supplierCode, status, sku, current, size));
    }

    @GetMapping("/sap")
    public R<Map<String, Object>> sap() {
        return R.ok(service.sap());
    }
}
