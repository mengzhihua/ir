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
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.mapper.CostRecordMapper;
import com.ir.snapshot.entity.ExtSnapshot;
import com.ir.snapshot.mapper.ExtSnapshotMapper;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.FinanceSnapshotMapper;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.SupplierScore;
import com.ir.snapshot.SupplierScoreMapper;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.mapper.SalesDailyMapper;
import com.ir.snapshot.entity.SalesPoint;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import com.ir.snapshot.mapper.WmsOrderSnapshotMapper;
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
    private final ExtSnapshotMapper extMapper;
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
            ExtSnapshotMapper extMapper,
            ClientFactory clients) {
        this.purchaseMapper = purchaseMapper;
        this.supplierScoreMapper = supplierScoreMapper;
        this.financeMapper = financeMapper;
        this.extMapper = extMapper;
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
            result.put("inventory", persistInventory(client.fetchInventory(), "OMS"));
            result.put("sales", persistSales(client.fetchDailySales(90)));
        } else if ("WMS".equals(code)) {
            WmsClient client = clients.wms(system);
            result.put("outbound", persistWmsOrders(client.fetchOutbound()));
            result.put("inventory", persistInventory(client.fetchInventorySummary(), "WMS"));
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
            int purchaseOrders = persistPurchases(client.fetchPurchaseOrders());
            int asns = persistPurchases(client.fetchAsns());
            int scores = persistSupplierScores(client.fetchSupplierScores());
            int snapshots = persistExt(code, clients.ecosystem(system).fetchSnapshots());
            result.put("purchaseOrders", purchaseOrders);
            result.put("asns", asns);
            result.put("supplierScores", scores);
            result.put("snapshots", snapshots);
            saveLog("SRM", "PURCHASE", "SUCCESS", purchaseOrders + asns, "采购订单/ASN 同步完成");
            saveLog("SRM", "SUPPLIER_SCORE", "SUCCESS", scores, "供应商评分同步完成");
        } else if ("SAP".equals(code)) {
            SapClient client = clients.sap(system);
            int stock = persistInventory(client.fetchStock(), null);
            int finance = persistFinance(client.fetchFinance());
            int snapshots = persistExt(code, clients.ecosystem(system).fetchSnapshots());
            result.put("stock", stock);
            result.put("finance", finance);
            result.put("snapshots", snapshots);
            saveLog("SAP", "STOCK", "SUCCESS", stock, "SAP 库存同步完成");
            saveLog("SAP", "FINANCE", "SUCCESS", finance, "SAP 财务指标同步完成");
        } else if (ClientFactory.ecosystemCode(code)) {
            int snapshots = persistExt(code, clients.ecosystem(system).fetchSnapshots());
            result.put("snapshots", snapshots);
        } else {
            throw new IntegrationException("不支持同步的系统: " + code);
        }
        system.setLastSyncAt(LocalDateTime.now());
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

    /** logSystem 为 null 时由调用方自行记录日志. */
    private int persistInventory(List<InventorySnapshot> rows, String logSystem) {
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
        if (logSystem != null) {
            saveLog(logSystem, "INVENTORY", "SUCCESS", rows.size(), "库存快照同步完成");
        }
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

    private int persistExt(String systemCode, List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> raw : rows) {
            String dataType = string(raw.get("dataType"));
            String bizKey = string(raw.get("bizKey"));
            if (dataType == null || bizKey == null) {
                continue;
            }
            ExtSnapshot row = new ExtSnapshot();
            row.setSourceSystem(systemCode);
            row.setDataType(dataType);
            row.setBizKey(bizKey);
            row.setStatus(string(raw.get("status")));
            row.setSku(string(raw.get("sku")));
            row.setQty(decimal(raw.get("qty")));
            row.setAmount(decimal(raw.get("amount")));
            row.setPlantCode(string(raw.get("plantCode")));
            row.setTitle(string(raw.get("title")));
            row.setSyncedAt(LocalDateTime.now());
            ExtSnapshot existing = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getSourceSystem, systemCode)
                    .eq(ExtSnapshot::getDataType, dataType)
                    .eq(ExtSnapshot::getBizKey, bizKey));
            if (existing == null) {
                extMapper.insert(row);
            } else {
                row.setId(existing.getId());
                extMapper.updateById(row);
            }
            count++;
        }
        saveLog(systemCode, "SNAPSHOT", "SUCCESS", count, "生态快照同步完成");
        return count;
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.trim().isEmpty() || "null".equals(text) ? null : text;
    }

    private static java.math.BigDecimal decimal(Object value) {
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return java.math.BigDecimal.ZERO;
        }
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
