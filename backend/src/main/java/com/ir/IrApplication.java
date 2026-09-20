package com.ir;

import com.ir.alert.service.AlertEngine;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.integration.sync.SyncService;
import com.ir.snapshot.mapper.CostRecordMapper;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
import com.ir.sandbox.service.SandboxService;
import com.ir.system.entity.User;
import com.ir.system.service.UserStore;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.ir.**.mapper", "com.ir.snapshot", "com.ir.balance",
        "com.ir.objective"})
public class IrApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(IrApplication.class);
    private final SyncService syncService;
    private final AlertEngine alertEngine;
    private final SandboxService sandboxService;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final CostRecordMapper costMapper;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final UserStore users;

    public IrApplication(
            SyncService syncService,
            AlertEngine alertEngine,
            SandboxService sandboxService,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            CostRecordMapper costMapper,
            PurchaseSnapshotMapper purchaseMapper,
            UserStore users) {
        this.syncService = syncService;
        this.alertEngine = alertEngine;
        this.sandboxService = sandboxService;
        this.orderMapper = orderMapper;
        this.wmsOrderMapper = wmsOrderMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.costMapper = costMapper;
        this.purchaseMapper = purchaseMapper;
        this.users = users;
    }

    public static void main(String[] args) {
        SpringApplication.run(IrApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if (emptySnapshots()) {
            syncService.syncAll();
        } else if (purchaseMapper.selectCount(null) == 0) {
            syncService.sync("SRM");
            syncService.sync("SAP");
        }
        alertEngine.evaluate();
        sandboxService.ensureBaseline();
        User admin = users.find("admin");
        if (admin != null && UserStore.verify("admin123", admin.getPassword())) {
            log.warn("admin仍使用默认密码，请尽快修改");
        }
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
