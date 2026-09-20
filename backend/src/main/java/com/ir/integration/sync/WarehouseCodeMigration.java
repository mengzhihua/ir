package com.ir.integration.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.common.WarehouseCodes;
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.CostRecordMapper;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class WarehouseCodeMigration {
    private static final Logger log = LoggerFactory.getLogger(WarehouseCodeMigration.class);

    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final OrderSnapshotMapper orderMapper;
    private final CostRecordMapper costMapper;

    public WarehouseCodeMigration(
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            OrderSnapshotMapper orderMapper,
            CostRecordMapper costMapper) {
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.wmsOrderMapper = wmsOrderMapper;
        this.orderMapper = orderMapper;
        this.costMapper = costMapper;
    }

    @Transactional
    public int migrate() {
        int count = migrateInventory();
        count += migrateSales();
        count += migrateWmsOrders();
        count += migrateOrders();
        count += migrateCosts();
        if (count > 0) {
            log.info("仓库编码迁移完成，处理 {} 条历史记录", count);
        }
        return count;
    }

    private int migrateInventory() {
        int count = 0;
        List<InventorySnapshot> rows = inventoryMapper.selectList(
                new LambdaQueryWrapper<InventorySnapshot>().isNotNull(
                        InventorySnapshot::getWarehouseCode));
        for (InventorySnapshot row : rows) {
            String canonical = WarehouseCodes.toOms(row.getWarehouseCode());
            if (canonical.equals(row.getWarehouseCode())) {
                continue;
            }
            InventorySnapshot existing = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<InventorySnapshot>()
                            .eq(InventorySnapshot::getSourceSystem, row.getSourceSystem())
                            .eq(InventorySnapshot::getWarehouseCode, canonical)
                            .eq(InventorySnapshot::getSku, row.getSku()));
            if (existing == null) {
                row.setWarehouseCode(canonical);
                inventoryMapper.updateById(row);
            } else {
                inventoryMapper.deleteById(row.getId());
            }
            count++;
        }
        return count;
    }

    private int migrateSales() {
        int count = 0;
        List<SalesDaily> rows = salesMapper.selectList(
                new LambdaQueryWrapper<SalesDaily>().isNotNull(
                        SalesDaily::getWarehouseCode));
        for (SalesDaily row : rows) {
            String canonical = WarehouseCodes.toOms(row.getWarehouseCode());
            if (canonical.equals(row.getWarehouseCode())) {
                continue;
            }
            SalesDaily existing = salesMapper.selectOne(
                    new LambdaQueryWrapper<SalesDaily>()
                            .eq(SalesDaily::getSalesDate, row.getSalesDate())
                            .eq(SalesDaily::getSku, row.getSku())
                            .eq(SalesDaily::getWarehouseCode, canonical)
                            .eq(SalesDaily::getChannelCode, row.getChannelCode()));
            if (existing == null) {
                row.setWarehouseCode(canonical);
                salesMapper.updateById(row);
            } else {
                existing.setQty(nz(existing.getQty()).add(nz(row.getQty())));
                existing.setAmount(nz(existing.getAmount()).add(nz(row.getAmount())));
                salesMapper.updateById(existing);
                salesMapper.deleteById(row.getId());
            }
            count++;
        }
        return count;
    }

    private int migrateWmsOrders() {
        int count = 0;
        List<WmsOrderSnapshot> rows = wmsOrderMapper.selectList(
                new LambdaQueryWrapper<WmsOrderSnapshot>().isNotNull(
                        WmsOrderSnapshot::getWarehouseCode));
        for (WmsOrderSnapshot row : rows) {
            String canonical = WarehouseCodes.toOms(row.getWarehouseCode());
            if (canonical.equals(row.getWarehouseCode())) {
                continue;
            }
            row.setWarehouseCode(canonical);
            wmsOrderMapper.updateById(row);
            count++;
        }
        return count;
    }

    private int migrateOrders() {
        int count = 0;
        List<OrderSnapshot> rows = orderMapper.selectList(
                new LambdaQueryWrapper<OrderSnapshot>().isNotNull(
                        OrderSnapshot::getWarehouseCode));
        for (OrderSnapshot row : rows) {
            String canonical = WarehouseCodes.toOms(row.getWarehouseCode());
            if (canonical.equals(row.getWarehouseCode())) {
                continue;
            }
            row.setWarehouseCode(canonical);
            orderMapper.updateById(row);
            count++;
        }
        return count;
    }

    private int migrateCosts() {
        int count = 0;
        List<CostRecord> rows = costMapper.selectList(
                new LambdaQueryWrapper<CostRecord>().isNotNull(
                        CostRecord::getWarehouseCode));
        for (CostRecord row : rows) {
            String canonical = WarehouseCodes.toOms(row.getWarehouseCode());
            if (canonical.equals(row.getWarehouseCode())) {
                continue;
            }
            row.setWarehouseCode(canonical);
            costMapper.updateById(row);
            count++;
        }
        return count;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
