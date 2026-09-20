package com.ir.forecast.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.common.CodeGenerator;
import com.ir.common.WarehouseCodes;
import com.ir.forecast.engine.ForecastEngine;
import com.ir.forecast.entity.CtForecast;
import com.ir.forecast.mapper.CtForecastMapper;
import com.ir.snapshot.entity.ExtSnapshot;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.SalesDaily;
import com.ir.snapshot.mapper.ExtSnapshotMapper;
import com.ir.snapshot.mapper.InventorySnapshotMapper;
import com.ir.snapshot.mapper.SalesDailyMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class ForecastService {
    private static final Set<String> CLOSED_INBOUND = new HashSet<>(Arrays.asList(
            "CANCELLED", "CANCELED", "CLOSED", "RECEIVED", "COMPLETED", "GR", "DONE", "REJECTED"));

    private final SalesDailyMapper salesMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final ExtSnapshotMapper extMapper;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final CtForecastMapper forecastMapper;
    private final ForecastEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public ForecastService(
            SalesDailyMapper salesMapper,
            InventorySnapshotMapper inventoryMapper,
            ExtSnapshotMapper extMapper,
            PurchaseSnapshotMapper purchaseMapper,
            CtForecastMapper forecastMapper,
            ForecastEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.salesMapper = salesMapper;
        this.inventoryMapper = inventoryMapper;
        this.extMapper = extMapper;
        this.purchaseMapper = purchaseMapper;
        this.forecastMapper = forecastMapper;
        this.engine = engine;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    public List<BigDecimal> history(String sku, String warehouseCode, int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<SalesDaily> rows = salesMapper.selectList(new LambdaQueryWrapper<SalesDaily>()
                .eq(SalesDaily::getSku, sku)
                .ge(SalesDaily::getSalesDate, from)
                .orderByAsc(SalesDaily::getSalesDate));
        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        for (SalesDaily row : rows) {
            if (warehouseCode != null && !warehouseCode.equals(row.getWarehouseCode())) {
                continue;
            }
            daily.put(row.getSalesDate(),
                    daily.getOrDefault(row.getSalesDate(), BigDecimal.ZERO)
                            .add(row.getQty()));
        }
        return new ArrayList<>(daily.values());
    }

    public Map<String, Object> run(
            String sku,
            String warehouseCode,
            int horizon,
            String method) {
        List<BigDecimal> history = history(sku, warehouseCode, 90);
        ForecastEngine.Result result = engine.forecast(history, horizon, method);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sku", sku);
        value.put("warehouseCode", warehouseCode);
        value.put("history", result.getHistory());
        value.put("forecast", result.getForecast());
        value.put("method", result.getMethod());
        value.put("mape", result.getMape());
        value.put("backtest", result.getBacktest());

        CtForecast forecast = new CtForecast();
        forecast.setRunNo("FC" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 20));
        forecast.setSku(sku);
        forecast.setWarehouseCode(warehouseCode);
        forecast.setMethod(result.getMethod());
        forecast.setHorizon(horizon);
        forecast.setMape(BigDecimal.valueOf(result.getMape()));
        forecast.setResultJson(write(value));
        forecastMapper.insert(forecast);
        value.put("runNo", forecast.getRunNo());
        return value;
    }

    public List<Map<String, Object>> replenish(
            String warehouseCode,
            String sku,
            int horizon,
            int serviceDays) {
        return replenish(warehouseCode, sku, horizon, serviceDays, 3);
    }

    public List<Map<String, Object>> replenish(
            String warehouseCode,
            String sku,
            int horizon,
            int serviceDays,
            int leadDays) {
        int lead = Math.max(0, leadDays);
        List<Map<String, Object>> result = new ArrayList<>();
        LambdaQueryWrapper<InventorySnapshot> query = new LambdaQueryWrapper<>();
        if (warehouseCode != null && !warehouseCode.trim().isEmpty()) {
            query.eq(InventorySnapshot::getWarehouseCode, warehouseCode);
        }
        if (sku != null && !sku.trim().isEmpty()) {
            query.eq(InventorySnapshot::getSku, sku.trim());
        }
        List<InventorySnapshot> inventory = inventoryMapper.selectList(query);
        Map<String, BigDecimal> inbound = inboundBySkuWarehouse();
        for (InventorySnapshot item : inventory) {
            Map<String, Object> forecast = run(
                    item.getSku(), item.getWarehouseCode(), horizon, "AUTO");
            BigDecimal demand = BigDecimal.ZERO;
            for (ForecastEngine.Point point : castPoints(forecast.get("forecast"))) {
                demand = demand.add(point.getQty());
            }
            BigDecimal daily = horizon == 0
                    ? BigDecimal.ZERO
                    : demand.divide(BigDecimal.valueOf(horizon), 6, RoundingMode.HALF_UP);
            BigDecimal safety = daily.multiply(BigDecimal.valueOf(serviceDays));
            BigDecimal inTransit = inbound.getOrDefault(
                    WarehouseCodes.stockKey(item.getSku(), item.getWarehouseCode()),
                    BigDecimal.ZERO);
            BigDecimal available = item.getQtyAvailable() == null
                    ? BigDecimal.ZERO
                    : item.getQtyAvailable();
            BigDecimal cover = available.add(inTransit);
            int coverDays = Math.max(1, lead + serviceDays);
            BigDecimal target = daily.multiply(BigDecimal.valueOf(coverDays));
            BigDecimal suggest = target.subtract(cover).max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>(forecast);
            row.put("forecastDemand", demand);
            row.put("onHand", item.getQtyOnHand());
            row.put("available", available);
            row.put("inTransit", inTransit);
            row.put("safety", safety);
            row.put("serviceDays", serviceDays);
            row.put("replenishLeadDays", lead);
            row.put("coverDays", coverDays);
            row.put("targetQty", target);
            row.put("suggestQty", suggest);
            row.put("suggestedQty", suggest);
            BigDecimal onHandDays = daily.signum() <= 0
                    ? BigDecimal.ZERO
                    : cover.divide(daily, 2, RoundingMode.HALF_UP);
            row.put("onHandDays", onHandDays);
            row.put("belowRop", suggest.signum() > 0);
            LocalDate stockout = stockoutDate(cover, daily);
            row.put("stockoutDate", stockout);
            row.put("orderByDate", orderByDate(stockout, lead));
            result.add(row);
        }
        result.sort((left, right) -> {
            int byGap = Boolean.compare(
                    Boolean.TRUE.equals(right.get("belowRop")),
                    Boolean.TRUE.equals(left.get("belowRop")));
            if (byGap != 0) {
                return byGap;
            }
            LocalDate leftDue = (LocalDate) left.get("orderByDate");
            LocalDate rightDue = (LocalDate) right.get("orderByDate");
            if (leftDue != null && rightDue != null) {
                int byDue = leftDue.compareTo(rightDue);
                if (byDue != 0) {
                    return byDue;
                }
            } else if (leftDue != null) {
                return -1;
            } else if (rightDue != null) {
                return 1;
            }
            return String.valueOf(left.get("sku")).compareTo(String.valueOf(right.get("sku")));
        });
        return result;
    }

    public Page<CtForecast> page(
            String sku,
            String warehouseCode,
            String method,
            long current,
            long size) {
        LambdaQueryWrapper<CtForecast> query = new LambdaQueryWrapper<>();
        if (sku != null && !sku.trim().isEmpty()) {
            query.eq(CtForecast::getSku, sku);
        }
        if (warehouseCode != null && !warehouseCode.trim().isEmpty()) {
            query.eq(CtForecast::getWarehouseCode, warehouseCode);
        }
        if (method != null && !method.trim().isEmpty()) {
            query.eq(CtForecast::getMethod, method);
        }
        query.orderByDesc(CtForecast::getCreatedAt);
        return forecastMapper.selectPage(new Page<>(current, size), query);
    }

    public List<CtAction> toActions(List<Map<String, Object>> rows) {
        List<CtAction> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(actions.createAndExecute(toActionRequest(row)));
        }
        return result;
    }

    public Map<String, BigDecimal> inboundBySku() {
        Map<String, BigDecimal> bySku = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : inboundBySkuWarehouse().entrySet()) {
            String sku = entry.getKey();
            int slash = sku.indexOf('/');
            if (slash > 0) {
                sku = sku.substring(0, slash);
            }
            bySku.merge(sku, entry.getValue(), BigDecimal::add);
        }
        return bySku;
    }

    public Map<String, BigDecimal> inboundBySkuWarehouse() {
        Map<String, BigDecimal> po = new HashMap<>();
        Map<String, BigDecimal> asn = new HashMap<>();
        List<ExtSnapshot> rows = extMapper.selectList(new LambdaQueryWrapper<ExtSnapshot>()
                .in(ExtSnapshot::getDataType, "PO", "ASN")
                .in(ExtSnapshot::getSourceSystem, "SRM", "SAP"));
        Map<String, BigDecimal> rootPo = new HashMap<>();
        Map<String, BigDecimal> rootAsn = new HashMap<>();
        for (PurchaseSnapshot row : purchaseMapper.selectList(
                new LambdaQueryWrapper<PurchaseSnapshot>()
                        .in(PurchaseSnapshot::getDocType, "PO", "ASN"))) {
            if (closedInbound(row.getStatus())) {
                continue;
            }
            String warehouse = WarehouseCodes.ofInbound(row.getPlantCode(), null);
            if (!row.lines().isEmpty()) {
                for (PurchaseSnapshot.PurchaseLine line : row.lines()) {
                    if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                        continue;
                    }
                    BigDecimal qty = line.getQty() == null ? BigDecimal.ZERO : line.getQty();
                    BigDecimal received = line.getReceivedQty() == null
                            ? BigDecimal.ZERO : line.getReceivedQty();
                    String key = WarehouseCodes.stockKey(line.getSku(), warehouse);
                    Map<String, BigDecimal> target = "ASN".equals(row.getDocType())
                            ? rootAsn : rootPo;
                    target.merge(key, qty.subtract(received).max(BigDecimal.ZERO),
                            BigDecimal::add);
                }
            } else {
                if (row.skuList().size() > 1
                        || row.getSku() == null || row.getSku().trim().isEmpty()) {
                    continue;
                }
                BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
                BigDecimal received = row.getReceivedQty() == null
                        ? BigDecimal.ZERO : row.getReceivedQty();
                String key = WarehouseCodes.stockKey(row.getSku(), warehouse);
                Map<String, BigDecimal> target = "ASN".equals(row.getDocType())
                        ? rootAsn : rootPo;
                target.merge(key, qty.subtract(received).max(BigDecimal.ZERO),
                        BigDecimal::add);
            }
        }
        for (ExtSnapshot row : rows) {
            if (row.getSku() == null || row.getSku().trim().isEmpty() || closedInbound(row.getStatus())) {
                continue;
            }
            BigDecimal qty = row.getQty() == null ? BigDecimal.ZERO : row.getQty();
            String key = WarehouseCodes.stockKey(row.getSku(), inboundWarehouse(row));
            if ("ASN".equals(row.getDataType())) {
                asn.merge(key, qty, BigDecimal::add);
            } else {
                po.merge(key, qty, BigDecimal::add);
            }
        }
        if (po.isEmpty() && asn.isEmpty()) {
            for (Map.Entry<String, BigDecimal> entry : rootPo.entrySet()) {
                po.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, BigDecimal> entry : rootAsn.entrySet()) {
                asn.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, BigDecimal> inbound = new HashMap<>(po);
        for (Map.Entry<String, BigDecimal> entry : asn.entrySet()) {
            BigDecimal ordered = inbound.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            inbound.put(entry.getKey(), ordered.max(entry.getValue()));
        }
        return inbound;
    }

    public BigDecimal inboundOf(String sku, String warehouse) {
        if (sku == null || sku.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (warehouse == null || warehouse.trim().isEmpty()) {
            return inboundBySku().getOrDefault(sku.trim(), BigDecimal.ZERO);
        }
        return inboundBySkuWarehouse().getOrDefault(
                WarehouseCodes.stockKey(sku, warehouse), BigDecimal.ZERO);
    }

    private Map<String, Object> toActionRequest(Map<String, Object> row) {
        String type = row.get("type") == null
                ? "SRM_PURCHASE_SUGGEST"
                : String.valueOf(row.get("type"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        Object qty = row.get("qty") != null
                ? row.get("qty")
                : (row.get("suggestQty") != null ? row.get("suggestQty") : row.get("suggestedQty"));
        if ("WMS_REPLENISH".equals(type)) {
            request.put("targetKey", row.get("warehouseCode"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("warehouseCode", row.get("warehouseCode"));
            params.put("sku", row.get("sku"));
            if (qty != null) {
                params.put("qty", qty);
            }
            request.put("params", params);
        } else {
            request.put("targetKey", row.get("sku"));
            Map<String, Object> params = new LinkedHashMap<>(row);
            if (qty != null) {
                params.put("qty", qty);
            }
            request.put("params", params);
        }
        return request;
    }

    private String inboundWarehouse(ExtSnapshot row) {
        return WarehouseCodes.ofInbound(row.getPlantCode(), extraWarehouse(row.getExtraJson()));
    }

    @SuppressWarnings("unchecked")
    private String extraWarehouse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> extra = objectMapper.readValue(json, Map.class);
            Object value = extra.get("warehouseCode");
            return value == null ? null : String.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean closedInbound(String status) {
        return status != null && CLOSED_INBOUND.contains(status.trim().toUpperCase());
    }

    private LocalDate stockoutDate(BigDecimal available, BigDecimal daily) {
        if (daily.signum() <= 0 || available.compareTo(BigDecimal.ZERO) <= 0) {
            return available.signum() <= 0 ? LocalDate.now() : null;
        }
        long days = available.divide(daily, 0, RoundingMode.DOWN).longValue();
        return LocalDate.now().plusDays(Math.max(1, days));
    }

    private LocalDate orderByDate(LocalDate stockout, int leadDays) {
        if (stockout == null) {
            return null;
        }
        LocalDate due = stockout.minusDays(Math.max(0, leadDays));
        LocalDate today = LocalDate.now();
        return due.isBefore(today) ? today : due;
    }

    @SuppressWarnings("unchecked")
    private List<ForecastEngine.Point> castPoints(Object value) {
        return value instanceof List
                ? (List<ForecastEngine.Point>) value
                : Collections.emptyList();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("保存预测结果失败", ex);
        }
    }
}
