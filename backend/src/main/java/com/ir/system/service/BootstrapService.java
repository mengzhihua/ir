package com.ir.system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.ir.alert.service.AlertEngine;
import com.ir.integration.sync.SyncService;
import com.ir.sandbox.service.AutoSandboxService;
import com.ir.sandbox.service.SandboxService;

@Component
public class BootstrapService implements CommandLineRunner {
    private final SyncService syncService;
    private final AlertEngine alertEngine;
    private final SandboxService sandboxService;
    private final AutoSandboxService autoSandboxService;

    @Value("${ir.sandbox.auto-on-startup:true}")
    private boolean autoOnStartup;

    public BootstrapService(
            SyncService syncService,
            AlertEngine alertEngine,
            SandboxService sandboxService,
            AutoSandboxService autoSandboxService) {
        this.syncService = syncService;
        this.alertEngine = alertEngine;
        this.sandboxService = sandboxService;
        this.autoSandboxService = autoSandboxService;
    }

    @Override
    public void run(String... args) {
        if (syncService.emptySnapshots()) {
            syncService.syncAll();
        }
        alertEngine.evaluate();
        sandboxService.ensureBaseline();
        if (autoOnStartup) {
            autoSandboxService.run();
        }
    }
}
