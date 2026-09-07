package com.ir.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.common.R;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.client.BaseUrlValidator;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.integration.sync.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final SyncService sync;
    private final CtSystemMapper systemMapper;
    private final CtSyncLogMapper syncLogMapper;
    private final BaseUrlValidator baseUrls;

    public IntegrationController(
            SyncService sync,
            CtSystemMapper systemMapper,
            CtSyncLogMapper syncLogMapper,
            BaseUrlValidator baseUrls) {
        this.sync = sync;
        this.systemMapper = systemMapper;
        this.syncLogMapper = syncLogMapper;
        this.baseUrls = baseUrls;
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
        CtSystem existing = systemMapper.selectById(id);
        if (existing == null) {
            return R.fail(404, "系统不存在");
        }
        if (request.getBaseUrl() != null) {
            baseUrls.validate(request.getBaseUrl());
            existing.setBaseUrl(request.getBaseUrl());
        }
        if (request.getCode() != null) existing.setCode(request.getCode());
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getAuthType() != null) existing.setAuthType(request.getAuthType());
        if (request.getUsername() != null) existing.setUsername(request.getUsername());
        if (request.getMode() != null) existing.setMode(request.getMode());
        if (request.getEnabled() != null) existing.setEnabled(request.getEnabled());
        if (request.getPassword() != null
                && !request.getPassword().trim().isEmpty()
                && !"******".equals(request.getPassword())) {
            existing.setPassword(request.getPassword());
        }
        if (request.getApiKey() != null
                && !request.getApiKey().trim().isEmpty()
                && !"******".equals(request.getApiKey())) {
            existing.setApiKey(request.getApiKey());
        }
        systemMapper.updateById(existing);
        return R.ok(systemMapper.selectById(id));
    }

    @PostMapping("/system")
    public R<CtSystem> create(@RequestBody CtSystem request) {
        baseUrls.validate(request.getBaseUrl());
        systemMapper.insert(request);
        return R.ok(systemMapper.selectById(request.getId()));
    }

    @DeleteMapping("/system/{id}")
    public R<Void> delete(@PathVariable Long id) {
        CtSystem system = systemMapper.selectById(id);
        if (system == null) {
            return R.fail(404, "系统不存在");
        }
        if (java.util.Arrays.asList("OMS", "TMS", "WMS", "BMS", "SRM")
                .contains(system.getCode())) {
            return R.fail(400, "内置系统不可删除");
        }
        systemMapper.deleteById(id);
        return R.ok();
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
    public R<Page<CtSyncLog>> logs(
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<CtSyncLog> query = new LambdaQueryWrapper<>();
        if (systemCode != null) {
            query.eq(CtSyncLog::getSystemCode, systemCode);
        }
        if (status != null) {
            query.eq(CtSyncLog::getStatus, status);
        }
        query.orderByDesc(CtSyncLog::getStartedAt);
        return R.ok(syncLogMapper.selectPage(new Page<>(current, size), query));
    }
}
