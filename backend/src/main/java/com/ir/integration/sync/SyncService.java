package com.ir.integration.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.integration.client.BmsClient;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.OmsClient;
import com.ir.integration.client.TmsClient;
import com.ir.integration.client.WmsClient;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.SalesDaily;
import com.ir.snapshot.SalesDailyMapper;
import com.ir.snapshot.SalesPoint;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncService {
    private final CtSystemMapper systemMapper;
    private final CtSyncLogMapper syncLogMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final CostRecordMapper costMapper;
    private final ClientFactory clients;

    public SyncService(
            CtSystemMapper systemMapper,
            CtSyncLogMapper syncLogMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            CostRecordMapper costMapper,
            ClientFactory clients) {
        this.systemMapper = systemMapper;
        this.syncLogMapper = syncLogMapper;
        this.orderMapper = orderMapper;
        this.wmsOrderMapper = wmsOrderMapper;
        this.shipmentMapper = shipmentMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.costMapper = costMapper;
        this.clients = clients;
    }

    @Transactional
    public synchronized Map<String, Object> syncAll() {
        List<CtSystem> systems = systemMapper.selectList(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getEnabled, true));
        Map<String, Object> result = new LinkedHashMap<>();
        for (CtSystem system : systems) {
            result.put(system.getCode(), sync(system.getCode()));
        }
        return result;
    }

    @Transactional
    public synchronized Map<String, Object> sync(String code) {
        CtSystem system = systemMapper.selectOne(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getCode, code));
        if (system == null || !Boolean.TRUE.equals(system.getEnabled())) {
            throw new IntegrationException("系统未启用: " + code);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if ("OMS".equals(code)) {
                OmsClient client = clients.oms(system);
                result.put("orders", persistOrders(client.fetchOrders()));
                result.put("inventory", persistInventory(client.fetchInventory()));
                result.put("sales", persistSales(client.fetchDailySales(90)));
            } else if ("WMS".equals(code)) {
                WmsClient client = clients.wms(system);
                result.put("outbound", persistWmsOrders(client.fetchOutbound()));
                result.put("inventory", persistInventory(client.fetchInventorySummary()));
            } else if ("TMS".equals(code)) {
                TmsClient client = clients.tms(system);
                result.put("shipments", persistShipments(client.fetchWaybills()));
                result.put("costs", persistCosts(client.fetchFreightBills(), "TMS"));
            } else if ("BMS".equals(code)) {
                BmsClient client = clients.bms(system);
                LocalDate to = LocalDate.now();
                result.put("costs", persistCosts(
                        client.fetchCosts(to.minusDays(90), to), "BMS"));
            }
            system.setLastHealthAt(LocalDateTime.now());
            system.setLastHealthOk(true);
            system.setLastError(null);
            systemMapper.updateById(system);
            result.put("ok", true);
            return result;
        } catch (RuntimeException ex) {
            system.setLastHealthAt(LocalDateTime.now());
            system.setLastHealthOk(false);
            system.setLastError(ex.getMessage());
            systemMapper.updateById(system);
            saveLog(code, "SYNC", "FAILED", 0, ex.getMessage());
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
                new LambdaQueryWrapper<CtSyncLog>().orderByDesc(CtSyncLog::getStartedAt));
    }

    private int persistOrders(List<OrderSnapshot> rows) {
        for (OrderSnapshot row : rows) {
            OrderSnapshot existing = orderMapper.selectOne(
                    new LambdaQueryWrapper<OrderSnapshot>()
                            .eq(OrderSnapshot::getOrderNo, row.getOrderNo()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                orderMapper.insert(row);
            } else {
                row.setId(existing.getId());
                orderMapper.updateById(row);
            }
        }
        saveLog("OMS", "ORDER", "SUCCESS", rows.size(), "订单快照同步完成");
        return rows.size();
    }

    private int persistWmsOrders(List<WmsOrderSnapshot> rows) {
        for (WmsOrderSnapshot row : rows) {
            WmsOrderSnapshot existing = wmsOrderMapper.selectOne(
                    new LambdaQueryWrapper<WmsOrderSnapshot>()
                            .eq(WmsOrderSnapshot::getCode, row.getCode()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                wmsOrderMapper.insert(row);
            } else {
                row.setId(existing.getId());
                wmsOrderMapper.updateById(row);
            }
        }
        saveLog("WMS", "ORDER", "SUCCESS", rows.size(), "出库快照同步完成");
        return rows.size();
    }

    private int persistShipments(List<ShipmentSnapshot> rows) {
        for (ShipmentSnapshot row : rows) {
            ShipmentSnapshot existing = shipmentMapper.selectOne(
                    new LambdaQueryWrapper<ShipmentSnapshot>()
                            .eq(ShipmentSnapshot::getWaybillCode, row.getWaybillCode()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                shipmentMapper.insert(row);
            } else {
                row.setId(existing.getId());
                shipmentMapper.updateById(row);
            }
        }
        saveLog("TMS", "SHIPMENT", "SUCCESS", rows.size(), "运输快照同步完成");
        return rows.size();
    }

    private int persistInventory(List<InventorySnapshot> rows) {
        for (InventorySnapshot row : rows) {
            InventorySnapshot existing = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<InventorySnapshot>()
                            .eq(InventorySnapshot::getSourceSystem, row.getSourceSystem())
                            .eq(InventorySnapshot::getWarehouseCode, row.getWarehouseCode())
                            .eq(InventorySnapshot::getSku, row.getSku()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                inventoryMapper.insert(row);
            } else {
                row.setId(existing.getId());
                inventoryMapper.updateById(row);
            }
        }
        saveLog("WMS", "INVENTORY", "SUCCESS", rows.size(), "库存快照同步完成");
        return rows.size();
    }

    private int persistSales(List<SalesPoint> rows) {
        for (SalesPoint point : rows) {
            SalesDaily row = new SalesDaily();
            row.setSalesDate(point.getSalesDate());
            row.setSku(point.getSku());
            row.setWarehouseCode(point.getWarehouseCode());
            row.setChannelCode(point.getChannelCode());
            row.setQty(point.getQty());
            row.setAmount(point.getAmount());
            SalesDaily existing = salesMapper.selectOne(
                    new LambdaQueryWrapper<SalesDaily>()
                            .eq(SalesDaily::getSalesDate, row.getSalesDate())
                            .eq(SalesDaily::getSku, row.getSku())
                            .eq(SalesDaily::getWarehouseCode, row.getWarehouseCode())
                            .eq(SalesDaily::getChannelCode, row.getChannelCode()));
            if (existing == null) {
                salesMapper.insert(row);
            } else {
                row.setId(existing.getId());
                salesMapper.updateById(row);
            }
        }
        saveLog("OMS", "ORDER", "SUCCESS", rows.size(), "日销量同步完成");
        return rows.size();
    }

    private int persistCosts(List<CostRecord> rows, String sourceSystem) {
        costMapper.delete(new LambdaQueryWrapper<CostRecord>()
                .eq(CostRecord::getSourceSystem, sourceSystem));
        for (CostRecord row : rows) {
            row.setSourceSystem(sourceSystem);
            costMapper.insert(row);
        }
        saveLog(sourceSystem, "COST", "SUCCESS", rows.size(), "成本同步完成");
        return rows.size();
    }

    private void saveLog(String systemCode, String type, String status, int rows, String message) {
        CtSyncLog log = new CtSyncLog();
        log.setSystemCode(systemCode);
        log.setDataType(type);
        log.setStatus(status);
        log.setRows(rows);
        log.setMessage(message);
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        syncLogMapper.insert(log);
    }
}
