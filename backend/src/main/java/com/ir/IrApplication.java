package com.ir;

import com.ir.integration.sync.SyncService;
import com.ir.alert.AlertEngine;
import com.ir.sandbox.SandboxService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IrApplication implements CommandLineRunner {
    private final SyncService syncService;
    private final AlertEngine alertEngine;
    private final SandboxService sandboxService;

    public IrApplication(SyncService syncService, AlertEngine alertEngine, SandboxService sandboxService) {
        this.syncService = syncService;
        this.alertEngine = alertEngine;
        this.sandboxService = sandboxService;
    }

    public static void main(String[] args) {
        SpringApplication.run(IrApplication.class, args);
    }

    @Override
    public void run(String... args) {
        syncService.syncAll();
        alertEngine.evaluate();
        sandboxService.ensureBaseline();
    }
}
