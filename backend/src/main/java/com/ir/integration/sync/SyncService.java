package com.ir.integration.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.integration.mapper.CtSystemMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncService {
    private final CtSystemMapper systemMapper;
    private final CtSyncLogMapper syncLogMapper;
    private final ClientFactory clients;
    private final SystemSyncWorker worker;

    public SyncService(
            CtSystemMapper systemMapper,
            CtSyncLogMapper syncLogMapper,
            ClientFactory clients,
            SystemSyncWorker worker) {
        this.systemMapper = systemMapper;
        this.syncLogMapper = syncLogMapper;
        this.clients = clients;
        this.worker = worker;
    }

    public synchronized Map<String, Object> syncAll() {
        List<CtSystem> systems = systemMapper.selectList(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getEnabled, true));
        Map<String, Object> result = new LinkedHashMap<>();
        for (CtSystem system : systems) {
            result.put(system.getCode(), sync(system.getCode()));
        }
        return result;
    }

    public synchronized Map<String, Object> sync(String code) {
        try {
            return worker.syncOne(code);
        } catch (RuntimeException ex) {
            recordFailure(code, ex);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("message", ex.getMessage());
            return result;
        }
    }

    @Scheduled(cron = "${ir.sync.cron:0 0/5 * * * ?}")
    public void scheduledSync() {
        syncAll();
    }

    public Map<String, Object> health(String code) {
        CtSystem system = systemMapper.selectOne(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getCode, code));
        if (system == null) {
            throw new IntegrationException("系统不存在: " + code);
        }
        boolean ok;
        if ("OMS".equals(code)) {
            ok = clients.oms(system).health();
        } else if ("WMS".equals(code)) {
            ok = clients.wms(system).health();
        } else if ("TMS".equals(code)) {
            ok = clients.tms(system).health();
        } else {
            ok = clients.bms(system).health();
        }
        system.setLastHealthAt(LocalDateTime.now());
        system.setLastHealthOk(ok);
        systemMapper.updateById(system);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemCode", code);
        result.put("ok", ok);
        result.put("lastHealthAt", system.getLastHealthAt());
        return result;
    }

    public List<CtSyncLog> logs() {
        return syncLogMapper.selectList(
                new LambdaQueryWrapper<CtSyncLog>()
                        .orderByDesc(CtSyncLog::getStartedAt));
    }

    private void recordFailure(String code, RuntimeException ex) {
        CtSystem system = systemMapper.selectOne(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getCode, code));
        if (system != null) {
            system.setLastHealthAt(LocalDateTime.now());
            system.setLastHealthOk(false);
            system.setLastError(ex.getMessage());
            systemMapper.updateById(system);
        }
        CtSyncLog log = new CtSyncLog();
        log.setSystemCode(code);
        log.setDataType("SYNC");
        log.setStatus("FAILED");
        log.setRows(0);
        log.setMessage(ex.getMessage());
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        syncLogMapper.insert(log);
    }
}
