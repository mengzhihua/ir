package com.ir.integration.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:warehouse-migration;MODE=MySQL;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WarehouseCodeMigrationTest {
    @Autowired
    private WarehouseCodeMigration migration;
    @Autowired
    private InventorySnapshotMapper inventoryMapper;
    @Autowired
    private SalesDailyMapper salesMapper;

    @Test
    void inventoryLegacyDuplicateIsRemoved() {
        inventoryMapper.delete(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "WMS")
                .eq(InventorySnapshot::getSku, "MIGRATION-SKU"));
        inventoryMapper.insert(inventory("WH01", 100));
        inventoryMapper.insert(inventory("WH-SH", 100));

        migration.migrate();

        List<InventorySnapshot> rows = inventoryMapper.selectList(
                new LambdaQueryWrapper<InventorySnapshot>()
                        .eq(InventorySnapshot::getSourceSystem, "WMS")
                        .eq(InventorySnapshot::getSku, "MIGRATION-SKU"));
        assertEquals(1, rows.size());
        assertEquals("WH-SH", rows.get(0).getWarehouseCode());
    }

    @Test
    void salesLegacyRowsAreUpdatedOrMerged() {
        LocalDate updatedDate = LocalDate.of(2090, 1, 1);
        LocalDate mergedDate = LocalDate.of(2090, 1, 2);
        salesMapper.delete(new LambdaQueryWrapper<SalesDaily>()
                .in(SalesDaily::getSalesDate, updatedDate, mergedDate)
                .eq(SalesDaily::getSku, "MIGRATION-SKU"));
        salesMapper.insert(sales(updatedDate, "WH02", 3, 7));
        salesMapper.insert(sales(mergedDate, "WH02", 4, 8));
        salesMapper.insert(sales(mergedDate, "WH-BJ", 6, 12));

        migration.migrate();

        SalesDaily updated = salesMapper.selectOne(new LambdaQueryWrapper<SalesDaily>()
                .eq(SalesDaily::getSalesDate, updatedDate)
                .eq(SalesDaily::getSku, "MIGRATION-SKU"));
        assertEquals("WH-BJ", updated.getWarehouseCode());
        SalesDaily merged = salesMapper.selectOne(new LambdaQueryWrapper<SalesDaily>()
                .eq(SalesDaily::getSalesDate, mergedDate)
                .eq(SalesDaily::getSku, "MIGRATION-SKU")
                .eq(SalesDaily::getWarehouseCode, "WH-BJ"));
        assertEquals(0, new BigDecimal("10").compareTo(merged.getQty()));
        assertEquals(0, new BigDecimal("20").compareTo(merged.getAmount()));
        assertEquals(1, salesMapper.selectCount(new LambdaQueryWrapper<SalesDaily>()
                .eq(SalesDaily::getSalesDate, mergedDate)
                .eq(SalesDaily::getSku, "MIGRATION-SKU")));
    }

    private InventorySnapshot inventory(String warehouse, int qty) {
        InventorySnapshot row = new InventorySnapshot();
        row.setSourceSystem("WMS");
        row.setWarehouseCode(warehouse);
        row.setSku("MIGRATION-SKU");
        row.setQtyOnHand(BigDecimal.valueOf(qty));
        row.setQtyAvailable(BigDecimal.valueOf(qty));
        row.setQtyReserved(BigDecimal.ZERO);
        row.setSafetyQty(BigDecimal.ZERO);
        return row;
    }

    private SalesDaily sales(LocalDate date, String warehouse, int qty, int amount) {
        SalesDaily row = new SalesDaily();
        row.setSalesDate(date);
        row.setSku("MIGRATION-SKU");
        row.setWarehouseCode(warehouse);
        row.setChannelCode("MIGRATION");
        row.setQty(BigDecimal.valueOf(qty));
        row.setAmount(BigDecimal.valueOf(amount));
        return row;
    }
}
