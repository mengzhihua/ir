package com.ir.integration.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.integration.client.BmsClient;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.OmsClient;
import com.ir.integration.client.SapClient;
import com.ir.integration.client.SrmClient;
import com.ir.integration.client.TmsClient;
import com.ir.integration.client.WmsClient;
import com.ir.integration.entity.CtSyncLog;
import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSyncLogMapper;
import com.ir.integration.mapper.CtSystemMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.FinanceSnapshotMapper;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.SupplierScore;
import com.ir.snapshot.SupplierScoreMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSyncWorker {
    private final CtSystemMapper systemMapper;
    private final CtSyncLogMapper syncLogMapper;
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final CostRecordMapper costMapper;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final SupplierScoreMapper supplierScoreMapper;
    private final FinanceSnapshotMapper financeMapper;
    private final ClientFactory clients;

    public SystemSyncWorker(
            CtSystemMapper systemMapper,
            CtSyncLogMapper syncLogMapper,
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            CostRecordMapper costMapper,
            PurchaseSnapshotMapper purchaseMapper,
            SupplierScoreMapper supplierScoreMapper,
            FinanceSnapshotMapper financeMapper,
            ClientFactory clients) {
        this.purchaseMapper = purchaseMapper;
        this.supplierScoreMapper = supplierScoreMapper;
        this.financeMapper = financeMapper;
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
    public Map<String, Object> syncOne(String code) {
        CtSystem system = systemMapper.selectOne(
                new LambdaQueryWrapper<CtSystem>().eq(CtSystem::getCode, code));
        if (system == null || !Boolean.TRUE.equals(system.getEnabled())) {
            throw new IntegrationException("系统未启用: " + code);
        }
        Map<String, Object> result = new LinkedHashMap<>();
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
        } else if ("SRM".equals(code)) {
            SrmClient client = clients.srm(system);
            result.put("purchaseOrders", persistPurchases(client.fetchPurchaseOrders()));
            result.put("asns", persistPurchases(client.fetchAsns()));
            result.put("supplierScores", persistSupplierScores(client.fetchSupplierScores()));
        } else if ("SAP".equals(code)) {
            SapClient client = clients.sap(system);
            result.put("stock", persistInventory(client.fetchStock()));
            result.put("finance", persistFinance(client.fetchFinance()));
        }
        system.setLastHealthAt(LocalDateTime.now());
        system.setLastHealthOk(true);
        system.setLastError(null);
        systemMapper.updateById(system);
        result.put("ok", true);
        return result;
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

    private int persistPurchases(List<PurchaseSnapshot> rows) {
        for (PurchaseSnapshot row : rows) {
            PurchaseSnapshot existing = purchaseMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseSnapshot>()
                            .eq(PurchaseSnapshot::getDocType, row.getDocType())
                            .eq(PurchaseSnapshot::getCode, row.getCode()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                purchaseMapper.insert(row);
            } else {
                row.setId(existing.getId());
                purchaseMapper.updateById(row);
            }
        }
        return rows.size();
    }

    private int persistSupplierScores(List<SupplierScore> rows) {
        for (SupplierScore row : rows) {
            SupplierScore existing = supplierScoreMapper.selectOne(
                    new LambdaQueryWrapper<SupplierScore>()
                            .eq(SupplierScore::getSupplierCode, row.getSupplierCode())
                            .eq(SupplierScore::getPeriod, row.getPeriod()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                supplierScoreMapper.insert(row);
            } else {
                row.setId(existing.getId());
                supplierScoreMapper.updateById(row);
            }
        }
        return rows.size();
    }

    private int persistFinance(List<FinanceSnapshot> rows) {
        for (FinanceSnapshot row : rows) {
            FinanceSnapshot existing = financeMapper.selectOne(
                    new LambdaQueryWrapper<FinanceSnapshot>()
                            .eq(FinanceSnapshot::getMetric, row.getMetric())
                            .eq(FinanceSnapshot::getDimension, row.getDimension()));
            row.setSyncedAt(LocalDateTime.now());
            if (existing == null) {
                financeMapper.insert(row);
            } else {
                row.setId(existing.getId());
                financeMapper.updateById(row);
            }
        }
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

    private void saveLog(
            String systemCode,
            String type,
            String status,
            int rows,
            String message) {
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
