package com.ir.forecast;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.SalesDaily;
import com.ir.snapshot.SalesDailyMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class ForecastService {
    private final SalesDailyMapper salesMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final CtForecastMapper forecastMapper;
    private final ForecastEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public ForecastService(
            SalesDailyMapper salesMapper,
            InventorySnapshotMapper inventoryMapper,
            CtForecastMapper forecastMapper,
            ForecastEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.salesMapper = salesMapper;
        this.inventoryMapper = inventoryMapper;
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
            int horizon,
            int serviceDays) {
        List<Map<String, Object>> result = new ArrayList<>();
        LambdaQueryWrapper<InventorySnapshot> query = new LambdaQueryWrapper<>();
        if (warehouseCode != null) {
            query.eq(InventorySnapshot::getWarehouseCode, warehouseCode);
        }
        List<InventorySnapshot> inventory = inventoryMapper.selectList(query);
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
            BigDecimal suggest = demand.add(safety)
                    .subtract(item.getQtyAvailable())
                    .max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>(forecast);
            row.put("forecastDemand", demand);
            row.put("onHand", item.getQtyOnHand());
            row.put("available", item.getQtyAvailable());
            row.put("inTransit", BigDecimal.ZERO);
            row.put("safety", safety);
            row.put("suggestQty", suggest);
            row.put("stockoutDate", stockoutDate(item.getQtyAvailable(), daily));
            result.add(row);
        }
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
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("type", "SRM_PURCHASE_SUGGEST");
            request.put("targetKey", row.get("sku"));
            request.put("params", row);
            result.add(actions.createAndExecute(request));
        }
        return result;
    }

    private LocalDate stockoutDate(BigDecimal available, BigDecimal daily) {
        if (daily.signum() <= 0 || available.compareTo(BigDecimal.ZERO) <= 0) {
            return available.signum() <= 0 ? LocalDate.now() : null;
        }
        long days = available.divide(daily, 0, RoundingMode.DOWN).longValue();
        return LocalDate.now().plusDays(Math.max(1, days));
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
