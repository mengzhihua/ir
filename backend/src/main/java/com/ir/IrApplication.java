package com.ir;

import com.ir.integration.sync.SyncService;
import com.ir.alert.AlertEngine;
import com.ir.sandbox.SandboxService;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.SalesDailyMapper;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshotMapper;
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
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final CostRecordMapper costMapper;

    public IrApplication(
            SyncService syncService,
            AlertEngine alertEngine,
            SandboxService sandboxService,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            CostRecordMapper costMapper) {
        this.syncService = syncService;
        this.alertEngine = alertEngine;
        this.sandboxService = sandboxService;
        this.orderMapper = orderMapper;
        this.wmsOrderMapper = wmsOrderMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.costMapper = costMapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(IrApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if (emptySnapshots()) {
            syncService.syncAll();
        }
        alertEngine.evaluate();
        sandboxService.ensureBaseline();
    }

    private boolean emptySnapshots() {
        return orderMapper.selectCount(null) == 0
                && wmsOrderMapper.selectCount(null) == 0
                && shipmentMapper.selectCount(null) == 0
                && inventoryMapper.selectCount(null) == 0
                && salesMapper.selectCount(null) == 0
                && costMapper.selectCount(null) == 0;
    }
}
