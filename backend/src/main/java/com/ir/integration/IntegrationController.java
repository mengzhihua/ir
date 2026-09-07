package com.ir.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.common.R;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.integration.sync.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final SyncService sync;
    private final CtSystemMapper systemMapper;
    private final CtSyncLogMapper syncLogMapper;

    public IntegrationController(
            SyncService sync,
            CtSystemMapper systemMapper,
            CtSyncLogMapper syncLogMapper) {
        this.sync = sync;
        this.systemMapper = systemMapper;
        this.syncLogMapper = syncLogMapper;
    }

    @GetMapping("/system")
    public R<List<CtSystem>> systems() {
        return R.ok(systemMapper.selectList(
                new LambdaQueryWrapper<CtSystem>()
                        .orderByAsc(CtSystem::getId)));
    }

    @PutMapping("/system/{id}")
    public R<CtSystem> update(
            @PathVariable Long id,
            @RequestBody CtSystem request) {
        request.setId(id);
        systemMapper.updateById(request);
        return R.ok(systemMapper.selectById(id));
    }

    @PostMapping("/system/{code}/health")
    public R<Map<String, Object>> health(@PathVariable String code) {
        return R.ok(sync.health(code));
    }

    @PostMapping("/sync")
    public R<Map<String, Object>> sync() {
        return R.ok(sync.syncAll());
    }

    @PostMapping("/sync/{code}")
    public R<Map<String, Object>> one(@PathVariable String code) {
        return R.ok(sync.sync(code));
    }

    @GetMapping("/sync-log/page")
    public R<Map<String, Object>> logs(
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CtSyncLog> query = new LambdaQueryWrapper<>();
        if (systemCode != null) {
            query.eq(CtSyncLog::getSystemCode, systemCode);
        }
        if (status != null) {
            query.eq(CtSyncLog::getStatus, status);
        }
        query.orderByDesc(CtSyncLog::getStartedAt);
        List<CtSyncLog> rows = syncLogMapper.selectList(query);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", rows.size());
        return R.ok(result);
    }
}
