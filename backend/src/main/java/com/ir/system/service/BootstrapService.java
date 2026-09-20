package com.ir.system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.ir.alert.service.AlertEngine;
import com.ir.integration.sync.SyncService;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.sandbox.service.AutoSandboxService;
import com.ir.sandbox.service.SandboxService;
import com.ir.system.entity.User;

@Component
public class BootstrapService implements CommandLineRunner {
    private final SyncService syncService;
    private final AlertEngine alertEngine;
    private final SandboxService sandboxService;
    private final AutoSandboxService autoSandboxService;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final UserStore users;

    @Value("${ir.sandbox.auto-on-startup:true}")
    private boolean autoOnStartup;

    public BootstrapService(
            SyncService syncService,
            AlertEngine alertEngine,
            SandboxService sandboxService,
            AutoSandboxService autoSandboxService,
            PurchaseSnapshotMapper purchaseMapper,
            UserStore users) {
        this.syncService = syncService;
        this.alertEngine = alertEngine;
        this.sandboxService = sandboxService;
        this.autoSandboxService = autoSandboxService;
        this.purchaseMapper = purchaseMapper;
        this.users = users;
    }

    @Override
    public void run(String... args) {
        if (syncService.emptySnapshots()) {
            syncService.syncAll();
        } else if (purchaseMapper.selectCount(null) == 0) {
            syncService.sync("SRM");
            syncService.sync("SAP");
        }
        alertEngine.evaluate();
        sandboxService.ensureBaseline();
        User admin = users.find("admin");
        if (admin != null && UserStore.verify("admin123", admin.getPassword())) {
            org.slf4j.LoggerFactory.getLogger(BootstrapService.class)
                    .warn("admin仍使用默认密码，请尽快修改");
        }
        if (autoOnStartup) {
            autoSandboxService.run();
        }
    }
}
