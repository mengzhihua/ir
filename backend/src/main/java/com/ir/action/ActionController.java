package com.ir.action;

import com.ir.common.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/action")
public class ActionController {
    private final ActionService service;

    public ActionController(ActionService service) {
        this.service = service;
    }

    @PostMapping
    public R<CtAction> create(@RequestBody Map<String, Object> request) {
        return R.ok(service.createAndExecute(request));
    }

    @PostMapping("/{id}/retry")
    public R<CtAction> retry(@PathVariable Long id) {
        return R.ok(service.retry(id));
    }

    @GetMapping("/page")
    public R<Page<CtAction>> page(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            String type,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            String targetKey,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1")
            long current,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
            long size) {
        return R.ok(service.page(type, status, targetKey, current, size));
    }

    @GetMapping("/types")
    public R<List<Map<String, Object>>> types() {
        return R.ok(service.types());
    }
}
