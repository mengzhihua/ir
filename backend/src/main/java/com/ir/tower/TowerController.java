package com.ir.tower;

import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tower")
public class TowerController {
    private final TowerService service;

    public TowerController(TowerService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(service.overview());
    }
}
