package com.ir.sandbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.SalesDaily;
import com.ir.snapshot.SalesDailyMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SandboxService {
    private final CtScenarioMapper scenarioMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final SalesDailyMapper salesMapper;
    private final OrderSnapshotMapper orderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final WmsOrderSnapshotMapper wmsOrderMapper;
    private final SandboxEngine engine;
    private final ActionService actions;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public SandboxService(
            CtScenarioMapper scenarioMapper,
            InventorySnapshotMapper inventoryMapper,
            SalesDailyMapper salesMapper,
            OrderSnapshotMapper orderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            WmsOrderSnapshotMapper wmsOrderMapper,
            SandboxEngine engine,
            ActionService actions,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.scenarioMapper = scenarioMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesMapper = salesMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.wmsOrderMapper = wmsOrderMapper;
        this.engine = engine;
        this.actions = actions;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    public synchronized CtScenario baseline() {
        CtScenario existing = scenarioMapper.selectOne(
                new LambdaQueryWrapper<CtScenario>()
                        .eq(CtScenario::getBaseline, true)
                        .orderByAsc(CtScenario::getId)
                        .last("LIMIT 1"));
        return existing == null
                ? createAndRun("基线场景", new ScenarioParams(), true)
                : existing;
    }

    public void ensureBaseline() {
        baseline();
    }

    public CtScenario create(String name, ScenarioParams params) {
        return saveScenario(name,
                params == null ? new ScenarioParams() : params.normalized(),
                false, false);
    }

    public CtScenario run(Long id) {
        CtScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            return null;
        }
        ScenarioParams params = read(scenario.getParamsJson(), ScenarioParams.class);
        params = params.normalized();
        return saveScenario(scenario.getName(), params,
                Boolean.TRUE.equals(scenario.getBaseline()), true, scenario);
    }

    public CtScenario get(Long id) {
        return scenarioMapper.selectById(id);
    }

    public Page<CtScenario> page(
            String name,
            String status,
            long current,
            long size) {
        LambdaQueryWrapper<CtScenario> query = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            query.like(CtScenario::getName, name.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            query.eq(CtScenario::getStatus, status);
        }
        query.orderByDesc(CtScenario::getCreatedAt);
        return scenarioMapper.selectPage(new Page<>(current, size), query);
    }

    public List<Map<String, Object>> compare(String ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        CtScenario baseline = baseline();
        Map<String, Object> baseResult = result(baseline);
        for (String value : ids.split(",")) {
            CtScenario scenario = get(Long.valueOf(value.trim()));
            if (scenario == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", scenario);
            BigDecimal baseCost = baseline.getTotalCost() == null
                    ? BigDecimal.ZERO : baseline.getTotalCost();
            BigDecimal cost = scenario.getTotalCost() == null
                    ? BigDecimal.ZERO : scenario.getTotalCost();
            BigDecimal saving = baseCost.signum() == 0
                    ? BigDecimal.ZERO
                    : baseCost.subtract(cost)
                    .divide(baseCost, 6, BigDecimal.ROUND_HALF_UP);
            row.put("savingPercent", saving);
            row.put("savingPct", saving);
            row.put("delta", deltas(baseResult, result(scenario)));
            result.add(row);
        }
        return result;
    }

    public List<CtAction> apply(Long id) {
        CtScenario scenario = get(id);
        if (scenario == null) {
            return new ArrayList<>();
        }
        ScenarioParams params = read(
                scenario.getParamsJson(), ScenarioParams.class).normalized();
        Map<String, Object> scenarioResult = result(scenario);
        if (!scenarioResult.containsKey("skuWarehouse")
                || !scenarioResult.containsKey("stockoutByWarehouseSku")) {
            CtScenario refreshed = run(id);
            if (refreshed != null) {
                scenario = refreshed;
                scenarioResult = result(scenario);
            }
        }
        List<CtAction> result = new ArrayList<>();
        Map<String, String> skuWarehouse = stringMap(
                scenarioResult.get("skuWarehouse"));
        Map<String, WmsOrderSnapshot> outboundByOrder =
                outboundByOrder();
        for (OrderSnapshot order : orderMapper.selectList(
                new LambdaQueryWrapper<OrderSnapshot>()
                        .in(OrderSnapshot::getStatus,
                                "CREATED", "AUDITED", "ALLOCATED"))) {
            String sku = orderSku(order, outboundByOrder);
            String targetWarehouse = skuWarehouse.get(sku);
            if (targetWarehouse == null
                    || targetWarehouse.trim().isEmpty()
                    || targetWarehouse.equals(order.getWarehouseCode())) {
                continue;
            }
            Map<String, Object> actionParams = new LinkedHashMap<>();
            actionParams.put("warehouseCode", targetWarehouse);
            result.add(createPending("OMS_REROUTE_WAREHOUSE",
                    order.getOrderNo(), actionParams));
            if (result.size() >= 50) {
                return result;
            }
        }

        List<ShipmentSnapshot> eligibleShipments = eligibleShipments();
        Map<String, Integer> targetCounts = carrierTargets(
                params.getCarrierMix(), eligibleShipments.size());
        Map<String, List<ShipmentSnapshot>> surplus =
                new LinkedHashMap<>();
        Map<String, Integer> currentCounts = new LinkedHashMap<>();
        for (ShipmentSnapshot shipment : eligibleShipments) {
            String carrier = carrier(shipment.getCarrierCode());
            currentCounts.put(carrier,
                    currentCounts.getOrDefault(carrier, 0) + 1);
            surplus.computeIfAbsent(carrier,
                    ignored -> new ArrayList<>()).add(shipment);
        }
        Map<String, Integer> deficits = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : targetCounts.entrySet()) {
            int current = currentCounts.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > current) {
                deficits.put(entry.getKey(), entry.getValue() - current);
            }
        }

        for (Map.Entry<String, List<ShipmentSnapshot>> entry
                : surplus.entrySet()) {
            int target = targetCounts.getOrDefault(entry.getKey(), 0);
            int current = currentCounts.getOrDefault(entry.getKey(), 0);
            if (current <= target) {
                continue;
            }
            int moves = Math.min(current - target, entry.getValue().size());
            for (int index = 0; index < moves; index++) {
                ShipmentSnapshot shipment = entry.getValue().get(index);
                String destination = largestDeficit(deficits);
                if (destination == null) {
                    break;
                }
                Map<String, Object> actionParams = new LinkedHashMap<>();
                actionParams.put("carrierCode", destination);
                result.add(createPending("TMS_SWITCH_CARRIER",
                        shipment.getWaybillCode(), actionParams));
                deficits.put(destination, deficits.get(destination) - 1);
                if (result.size() >= 50) {
                    return result;
                }
            }
        }

        for (Map.Entry<String, Object> entry
                : objectMap(scenarioResult.get("stockoutByWarehouseSku"))
                .entrySet()) {
            BigDecimal quantity = decimal(entry.getValue());
            String targetKey = entry.getKey();
            int separator = targetKey.indexOf('/');
            if (quantity.signum() <= 0 || separator <= 0
                    || separator == targetKey.length() - 1) {
                continue;
            }
            String warehouse = targetKey.substring(0, separator);
            String sku = targetKey.substring(separator + 1);
            Map<String, Object> actionParams = new LinkedHashMap<>();
            actionParams.put("warehouseCode", warehouse);
            actionParams.put("sku", sku);
            actionParams.put("qty", quantity);
            result.add(createPending("WMS_REPLENISH", targetKey,
                    actionParams));
            if (result.size() >= 50) {
                return result;
            }
        }
        return result;
    }

    private CtAction createPending(
            String type,
            String targetKey,
            Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("targetKey", targetKey);
        request.put("params", params);
        return actions.createPending(request);
    }

    private Map<String, WmsOrderSnapshot> outboundByOrder() {
        Map<String, WmsOrderSnapshot> result = new LinkedHashMap<>();
        for (WmsOrderSnapshot row : wmsOrderMapper.selectList(null)) {
            if (row.getCode() != null) {
                result.put(row.getCode(), row);
            }
            if (row.getExternalNo() != null) {
                result.put(row.getExternalNo(), row);
            }
        }
        return result;
    }

    private String orderSku(
            OrderSnapshot order,
            Map<String, WmsOrderSnapshot> outboundByOrder) {
        if (order.getSku() != null && !order.getSku().trim().isEmpty()) {
            return order.getSku();
        }
        WmsOrderSnapshot outbound = outboundByOrder.get(order.getOrderNo());
        if (outbound == null && order.getWmsOrderNo() != null) {
            outbound = outboundByOrder.get(order.getWmsOrderNo());
        }
        return outbound == null ? null : outbound.getSku();
    }

    private List<ShipmentSnapshot> eligibleShipments() {
        List<ShipmentSnapshot> result = new ArrayList<>();
        List<String> terminalStatuses = Arrays.asList(
                "DELIVERED", "SIGNED", "CANCELLED", "CLOSED");
        for (ShipmentSnapshot shipment : shipmentMapper.selectList(null)) {
            if ("IN_TRANSIT".equals(shipment.getStatus())
                    || (!terminalStatuses.contains(shipment.getStatus())
                    && (shipment.getCarrierCode() == null
                    || shipment.getCarrierCode().trim().isEmpty()))) {
                result.add(shipment);
            }
        }
        return result;
    }

    private Map<String, Integer> carrierTargets(
            Map<String, BigDecimal> shares,
            int total) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (total <= 0) {
            return result;
        }
        BigDecimal shareTotal = BigDecimal.ZERO;
        for (BigDecimal share : shares.values()) {
            if (share != null && share.signum() > 0) {
                shareTotal = shareTotal.add(share);
            }
        }
        if (shareTotal.signum() == 0) {
            if (!shares.isEmpty()) {
                result.put(shares.keySet().iterator().next(), total);
            }
            return result;
        }
        int allocated = 0;
        List<CarrierAllocation> allocations = new ArrayList<>();
        int insertionOrder = 0;
        for (Map.Entry<String, BigDecimal> entry : shares.entrySet()) {
            BigDecimal share = entry.getValue() == null
                    ? BigDecimal.ZERO : entry.getValue();
            if (share.signum() > 0) {
                BigDecimal exact = share
                        .divide(shareTotal, 12, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(total));
                int floor = exact.setScale(0, BigDecimal.ROUND_FLOOR)
                        .intValue();
                allocations.add(new CarrierAllocation(
                        entry.getKey(),
                        share,
                        exact.subtract(BigDecimal.valueOf(floor)),
                        floor,
                        insertionOrder));
                result.put(entry.getKey(), floor);
                allocated += floor;
            } else {
                result.put(entry.getKey(), 0);
            }
            insertionOrder++;
        }
        allocations.sort(Comparator
                .comparing(CarrierAllocation::remainder).reversed()
                .thenComparing(CarrierAllocation::share, Comparator.reverseOrder())
                .thenComparingInt(CarrierAllocation::insertionOrder));
        int remaining = total - allocated;
        for (int index = 0; index < remaining; index++) {
            CarrierAllocation allocation = allocations.get(index);
            result.put(allocation.key(), result.get(allocation.key()) + 1);
        }
        return result;
    }

    private static class CarrierAllocation {
        private final String key;
        private final BigDecimal share;
        private final BigDecimal remainder;
        private final int insertionOrder;

        private CarrierAllocation(
                String key,
                BigDecimal share,
                BigDecimal remainder,
                int floor,
                int insertionOrder) {
            this.key = key;
            this.share = share;
            this.remainder = remainder;
            this.insertionOrder = insertionOrder;
        }

        private String key() {
            return key;
        }

        private BigDecimal share() {
            return share;
        }

        private BigDecimal remainder() {
            return remainder;
        }

        private int insertionOrder() {
            return insertionOrder;
        }
    }

    private String largestDeficit(Map<String, Integer> deficits) {
        String result = null;
        int highest = 0;
        for (Map.Entry<String, Integer> entry : deficits.entrySet()) {
            if (entry.getValue() > highest) {
                result = entry.getKey();
                highest = entry.getValue();
            }
        }
        return result;
    }

    private String carrier(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : objectMap(value).entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map
                ? (Map<String, Object>) value
                : Collections.emptyMap();
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO
                : new BigDecimal(String.valueOf(value));
    }

    private CtScenario createAndRun(
            String name,
            ScenarioParams params,
            boolean baseline) {
        return saveScenario(name, params, baseline, false);
    }

    private CtScenario saveScenario(
            String name,
            ScenarioParams params,
            boolean baseline,
            boolean update) {
        return saveScenario(name, params, baseline, update, null);
    }

    private CtScenario saveScenario(
            String name,
            ScenarioParams params,
            boolean baseline,
            boolean update,
            CtScenario existing) {
        params = params == null ? new ScenarioParams() : params.normalized();
        SandboxEngine.Result result = engine.run(params, baselineData());
        CtScenario scenario = existing == null ? new CtScenario() : existing;
        if (scenario.getScenarioNo() == null) {
            scenario.setScenarioNo(codes.next("SC"));
        }
        scenario.setName(name);
        scenario.setBaseline(baseline);
        scenario.setParamsJson(write(params));
        scenario.setResultJson(write(result));
        scenario.setStatus("RUN");
        scenario.setTotalCost(result.getTotalCost());
        scenario.setServiceLevel(result.getServiceLevel());
        if (update) {
            scenarioMapper.updateById(scenario);
        } else {
            scenarioMapper.insert(scenario);
        }
        return scenario;
    }

    private BaselineData baselineData() {
        BaselineData data = new BaselineData();
        data.setInventory(inventoryMapper.selectList(null));
        List<SalesDaily> sales = salesMapper.selectList(
                new LambdaQueryWrapper<SalesDaily>()
                        .orderByAsc(SalesDaily::getSalesDate));
        for (SalesDaily row : sales) {
            data.getDemandBySku()
                    .computeIfAbsent(row.getSku(), key -> new ArrayList<>())
                    .add(row.getQty());
            data.getSkuWarehouse().putIfAbsent(row.getSku(), row.getWarehouseCode());
            addShare(data.getChannelShare(), row.getSku(),
                    row.getChannelCode(), row.getQty());
        }
        for (OrderSnapshot row : orderMapper.selectList(null)) {
            addShare(data.getRegionShare(), "*",
                    region(row.getProvince()), BigDecimal.ONE);
        }
        for (String sku : data.getDemandBySku().keySet()) {
            if (!data.getRegionShare().containsKey(sku)) {
                data.getRegionShare().put(sku,
                        new LinkedHashMap<>(data.getRegionShare().get("*")));
            }
        }
        normalizeShares(data.getChannelShare());
        normalizeShares(data.getRegionShare());
        return data;
    }

    void addShare(
            Map<String, Map<String, BigDecimal>> shares,
            String key,
            String dimension,
            BigDecimal amount) {
        Map<String, BigDecimal> values = shares.computeIfAbsent(
                key, ignored -> new LinkedHashMap<>());
        values.put(dimension, values.getOrDefault(dimension,
                BigDecimal.ZERO).add(amount));
    }

    void normalizeShares(
            Map<String, Map<String, BigDecimal>> shares) {
        for (Map<String, BigDecimal> values : shares.values()) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values.values()) {
            total = total.add(value);
        }
            if (total.signum() == 0) {
                continue;
            }
        for (String name : new ArrayList<>(values.keySet())) {
            values.put(name, values.get(name).divide(total, 6,
                    BigDecimal.ROUND_HALF_UP));
        }
        }
    }

    private String region(String province) {
        if (province != null && (province.contains("北京")
                || province.contains("天津")
                || province.contains("河北")
                || province.contains("山东"))) {
            return "华北";
        }
        if (province != null && (province.contains("广东")
                || province.contains("广西")
                || province.contains("海南"))) {
            return "华南";
        }
        return "华东";
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("场景参数格式错误", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("场景结果保存失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(CtScenario scenario) {
        return read(scenario.getResultJson(), Map.class);
    }

    private Map<String, Object> deltas(
            Map<String, Object> baseline,
            Map<String, Object> scenario) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("totalCost", difference(scenario, baseline, "totalCost"));
        delta.put("serviceLevel", difference(scenario, baseline, "serviceLevel"));
        delta.put("stockoutUnits", difference(scenario, baseline,
                "stockoutUnits"));
        delta.put("avgLeadDays", difference(scenario, baseline,
                "avgLeadDays"));
        return delta;
    }

    private BigDecimal difference(
            Map<String, Object> left,
            Map<String, Object> right,
            String key) {
        BigDecimal leftValue = new BigDecimal(String.valueOf(
                left.getOrDefault(key, 0)));
        BigDecimal rightValue = new BigDecimal(String.valueOf(
                right.getOrDefault(key, 0)));
        return leftValue.subtract(rightValue);
    }
}
