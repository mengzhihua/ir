package com.ir.integration.sync;

import com.ir.integration.client.*;
import com.ir.snapshot.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SyncService {
    private final DataStore store; private final ClientFactory factory;
    private final Map<String, LocalDateTime> lastSync = new LinkedHashMap<>();
    private final Map<String, Boolean> health = new LinkedHashMap<>();
    public SyncService(DataStore store, ClientFactory factory) { this.store=store;this.factory=factory; }
    public synchronized Map<String,Object> syncAll() { sync("OMS"); sync("WMS"); sync("TMS"); sync("BMS"); return status(); }
    public synchronized Map<String,Object> sync(String code) {
        if ("OMS".equals(code)) { factory.oms("MOCK","").fetchOrders(); }
        lastSync.put(code, LocalDateTime.now()); health.put(code, true);
        return status();
    }
    @Scheduled(cron="${ir.sync.cron:0 0/5 * * * ?}")
    public void scheduled() { syncAll(); }
    public Map<String,Object> health(String code) { sync(code); Map<String,Object> m=new LinkedHashMap<>();m.put("systemCode",code);m.put("ok",health.get(code));m.put("lastSyncAt",lastSync.get(code));return m; }
    public Map<String,Object> status() { Map<String,Object> m=new LinkedHashMap<>();m.put("lastSync",new LinkedHashMap<>(lastSync));m.put("health",new LinkedHashMap<>(health));return m; }
}
