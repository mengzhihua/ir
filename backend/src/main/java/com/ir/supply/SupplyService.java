package com.ir.supply;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.FinanceSnapshotMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.SupplierScore;
import com.ir.snapshot.SupplierScoreMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应协同视图:供应商绩效、采购订单/ASN 状态、SAP 库存与财务快照。
 */
@Service
public class SupplyService {
    private final PurchaseSnapshotMapper purchases;
    private final SupplierScoreMapper scores;
    private final InventorySnapshotMapper inventory;
    private final FinanceSnapshotMapper finance;

    public SupplyService(
            PurchaseSnapshotMapper purchases,
            SupplierScoreMapper scores,
            InventorySnapshotMapper inventory,
            FinanceSnapshotMapper finance) {
        this.purchases = purchases;
        this.scores = scores;
        this.inventory = inventory;
        this.finance = finance;
    }

    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> suppliers = new LinkedHashMap<>();
        for (SupplierScore score : scores.selectList(new LambdaQueryWrapper<SupplierScore>()
                .orderByDesc(SupplierScore::getPeriod))) {
            if (!suppliers.containsKey(score.getSupplierCode())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("supplierCode", score.getSupplierCode());
                row.put("period", score.getPeriod());
                row.put("avgScore", score.getAvgScore());
                row.put("grade", score.getGrade());
                row.put("onTimeRate", score.getOnTimeRate());
                row.put("qualityRate", score.getQualityRate());
                row.put("qtyAccuracy", score.getQtyAccuracy());
                row.put("openPo", 0);
                row.put("openAmount", BigDecimal.ZERO);
                row.put("delayedAsn", 0);
                suppliers.put(score.getSupplierCode(), row);
            }
        }
        Map<String, Integer> poStatus = new LinkedHashMap<>();
        Map<String, Integer> asnStatus = new LinkedHashMap<>();
        int delayedAsn = 0;
        BigDecimal openAmount = BigDecimal.ZERO;
        List<Map<String, Object>> delayed = new ArrayList<>();
        for (PurchaseSnapshot doc : purchases.selectList(null)) {
            Map<String, Object> supplier = suppliers.computeIfAbsent(
                    doc.getSupplierCode() == null ? "UNKNOWN" : doc.getSupplierCode(), code -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("supplierCode", code);
                        row.put("openPo", 0);
                        row.put("openAmount", BigDecimal.ZERO);
                        row.put("delayedAsn", 0);
                        return row;
                    });
            if ("PO".equals(doc.getDocType())) {
                poStatus.merge(doc.getStatus(), 1, Integer::sum);
                if (!Arrays.asList("CLOSED", "CANCELLED").contains(doc.getStatus())) {
                    supplier.put("openPo", (Integer) supplier.get("openPo") + 1);
                    BigDecimal amount = doc.getAmount() == null ? BigDecimal.ZERO : doc.getAmount();
                    supplier.put("openAmount", ((BigDecimal) supplier.get("openAmount")).add(amount));
                    openAmount = openAmount.add(amount);
                }
            } else {
                asnStatus.merge(doc.getStatus(), 1, Integer::sum);
                boolean late = !Arrays.asList("RECEIVED", "CANCELLED").contains(doc.getStatus())
                        && ("DELAYED".equals(doc.getStatus())
                        || (doc.getExpectedDate() != null && doc.getExpectedDate().isBefore(today)));
                if (late) {
                    delayedAsn++;
                    supplier.put("delayedAsn", (Integer) supplier.get("delayedAsn") + 1);
                    delayed.add(asnRow(doc));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suppliers", new ArrayList<>(suppliers.values()));
        result.put("poStatus", poStatus);
        result.put("asnStatus", asnStatus);
        result.put("delayedAsn", delayedAsn);
        result.put("openAmount", openAmount);
        result.put("delayedList", delayed);
        result.put("sap", sap());
        return result;
    }

    public Page<PurchaseSnapshot> page(String docType, String supplierCode, String status,
                                       String sku, long current, long size) {
        LambdaQueryWrapper<PurchaseSnapshot> query = new LambdaQueryWrapper<>();
        if (docType != null && !docType.isEmpty()) {
            query.eq(PurchaseSnapshot::getDocType, docType);
        }
        if (supplierCode != null && !supplierCode.isEmpty()) {
            query.eq(PurchaseSnapshot::getSupplierCode, supplierCode);
        }
        if (status != null && !status.isEmpty()) {
            query.eq(PurchaseSnapshot::getStatus, status);
        }
        if (sku != null && !sku.isEmpty()) {
            query.eq(PurchaseSnapshot::getSku, sku);
        }
        query.orderByAsc(PurchaseSnapshot::getExpectedDate);
        return purchases.selectPage(new Page<>(current, size), query);
    }

    public Map<String, Object> sap() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> finance = new LinkedHashMap<>();
        for (FinanceSnapshot row : this.finance.selectList(null)) {
            finance.put(row.getMetric(), row.getAmount());
        }
        result.put("finance", finance);
        Map<String, BigDecimal> byPlant = new LinkedHashMap<>();
        Map<String, BigDecimal> bySku = new LinkedHashMap<>();
        for (InventorySnapshot item : inventory.selectList(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "SAP"))) {
            byPlant.merge(item.getWarehouseCode(), item.getQtyOnHand(), BigDecimal::add);
            bySku.merge(item.getSku(), item.getQtyOnHand(), BigDecimal::add);
        }
        Map<String, BigDecimal> wmsBySku = new LinkedHashMap<>();
        for (InventorySnapshot item : inventory.selectList(new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSourceSystem, "WMS"))) {
            wmsBySku.merge(item.getSku(), item.getQtyOnHand(), BigDecimal::add);
        }
        List<Map<String, Object>> reconcile = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : bySku.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sku", e.getKey());
            row.put("sapQty", e.getValue());
            row.put("wmsQty", wmsBySku.getOrDefault(e.getKey(), BigDecimal.ZERO));
            row.put("diff", e.getValue().subtract(wmsBySku.getOrDefault(e.getKey(), BigDecimal.ZERO)));
            reconcile.add(row);
        }
        result.put("stockByPlant", byPlant);
        result.put("stockReconcile", reconcile);
        return result;
    }

    private static Map<String, Object> asnRow(PurchaseSnapshot doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", doc.getCode());
        row.put("poCode", doc.getRefCode());
        row.put("supplierCode", doc.getSupplierCode());
        row.put("sku", doc.getSku());
        row.put("qty", doc.getQty());
        row.put("status", doc.getStatus());
        row.put("expectedDate", doc.getExpectedDate());
        return row;
    }
}
