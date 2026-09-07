package com.ir.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.SalesDailyMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SandboxServiceTest {
    @Test
    void sharesAccumulateBeforeNormalization() {
        Map<String, Map<String, BigDecimal>> shares = new LinkedHashMap<>();
        SandboxService service = new SandboxService(
                null, null, null, null, null, null, null, null, null, null);
        service.addShare(shares, "SKU001", "A", BigDecimal.ONE);
        service.addShare(shares, "SKU001", "B", BigDecimal.ONE);
        service.addShare(shares, "SKU001", "A", BigDecimal.ONE);
        service.normalizeShares(shares);
        assertEquals(0, BigDecimal.valueOf(2).divide(BigDecimal.valueOf(3), 6,
                BigDecimal.ROUND_HALF_UP).compareTo(
                shares.get("SKU001").get("A")));
        assertEquals(0, BigDecimal.valueOf(1).divide(BigDecimal.valueOf(3), 6,
                BigDecimal.ROUND_HALF_UP).compareTo(
                shares.get("SKU001").get("B")));
    }

    @Test
    void applyReroutesOpenOrdersUsingSkuWarehouse() {
        CtScenario scenario = scenario(
                "{\"allocationStrategy\":\"NEAREST\"}",
                "{\"skuWarehouse\":{\"SKU001\":\"WH-TARGET\"}}");
        CtScenarioMapper scenarios = mock(CtScenarioMapper.class);
        OrderSnapshotMapper orders = mock(OrderSnapshotMapper.class);
        InventorySnapshotMapper inventory = mock(InventorySnapshotMapper.class);
        SalesDailyMapper sales = mock(SalesDailyMapper.class);
        ShipmentSnapshotMapper shipmentMapper = mock(ShipmentSnapshotMapper.class);
        WmsOrderSnapshotMapper outbound = mock(WmsOrderSnapshotMapper.class);
        ActionService actions = mock(ActionService.class);
        OrderSnapshot order = new OrderSnapshot();
        order.setOrderNo("SO-1");
        order.setSku("SKU001");
        order.setWarehouseCode("WH-OLD");
        order.setStatus("CREATED");
        when(scenarios.selectById(1L)).thenReturn(scenario);
        when(inventory.selectList(any())).thenReturn(new ArrayList<>());
        when(sales.selectList(any())).thenReturn(new ArrayList<>());
        when(orders.selectList(any())).thenReturn(Arrays.asList(order));
        when(outbound.selectList(any())).thenReturn(new ArrayList<>());
        when(shipmentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(actions.createPending(any())).thenAnswer(invocation ->
                action(invocation.getArgument(0)));

        SandboxService service = service(
                scenarios, inventory, sales, orders, shipmentMapper, outbound,
                actions);
        List<CtAction> created = service.apply(1L);

        assertEquals(1, created.size());
        assertEquals("OMS_REROUTE_WAREHOUSE", created.get(0).getType());
        assertEquals("SO-1", created.get(0).getTargetKey());
        assertEquals("{\"warehouseCode\":\"WH-TARGET\"}",
                created.get(0).getParamsJson());
    }

    @Test
    void applyRebalancesCarriersBySurplusAndDeficit() {
        CtScenario scenario = scenario(
                "{\"carrierMix\":{\"SF\":0.8,\"JDL\":0.1,\"SELF\":0.1}}",
                "{}");
        CtScenarioMapper scenarios = mock(CtScenarioMapper.class);
        OrderSnapshotMapper orders = mock(OrderSnapshotMapper.class);
        InventorySnapshotMapper inventory = mock(InventorySnapshotMapper.class);
        SalesDailyMapper sales = mock(SalesDailyMapper.class);
        ShipmentSnapshotMapper shipmentMapper = mock(ShipmentSnapshotMapper.class);
        WmsOrderSnapshotMapper outbound = mock(WmsOrderSnapshotMapper.class);
        ActionService actions = mock(ActionService.class);
        List<ShipmentSnapshot> rows = new ArrayList<>();
        rows.addAll(shipments("SF", 4));
        rows.addAll(shipments("JDL", 3));
        rows.addAll(shipments("SELF", 3));
        when(scenarios.selectById(1L)).thenReturn(scenario);
        when(inventory.selectList(any())).thenReturn(new ArrayList<>());
        when(sales.selectList(any())).thenReturn(new ArrayList<>());
        when(orders.selectList(any())).thenReturn(new ArrayList<>());
        when(outbound.selectList(any())).thenReturn(new ArrayList<>());
        when(shipmentMapper.selectList(any())).thenReturn(rows);
        when(actions.createPending(any())).thenAnswer(invocation ->
                action(invocation.getArgument(0)));

        SandboxService service = service(
                scenarios, inventory, sales, orders, shipmentMapper, outbound,
                actions);
        List<CtAction> created = service.apply(1L);

        assertEquals(4, created.size());
        assertEquals("TMS_SWITCH_CARRIER", created.get(0).getType());
        assertEquals("SF", readParams(created.get(0)).get("carrierCode"));
        assertEquals("SF", readParams(created.get(3)).get("carrierCode"));
    }

    @Test
    void applyCreatesReplenishmentPerWarehouseSkuStockout() {
        CtScenario scenario = scenario(
                "{}",
                "{\"stockoutByWarehouseSku\":{\"WH-1/SKU001\":3.50}}");
        CtScenarioMapper scenarios = mock(CtScenarioMapper.class);
        OrderSnapshotMapper orders = mock(OrderSnapshotMapper.class);
        InventorySnapshotMapper inventory = mock(InventorySnapshotMapper.class);
        SalesDailyMapper sales = mock(SalesDailyMapper.class);
        ShipmentSnapshotMapper shipmentMapper = mock(ShipmentSnapshotMapper.class);
        WmsOrderSnapshotMapper outbound = mock(WmsOrderSnapshotMapper.class);
        ActionService actions = mock(ActionService.class);
        when(scenarios.selectById(1L)).thenReturn(scenario);
        when(inventory.selectList(any())).thenReturn(new ArrayList<>());
        when(sales.selectList(any())).thenReturn(new ArrayList<>());
        when(orders.selectList(any())).thenReturn(new ArrayList<>());
        when(outbound.selectList(any())).thenReturn(new ArrayList<>());
        when(shipmentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(actions.createPending(any())).thenAnswer(invocation ->
                action(invocation.getArgument(0)));

        SandboxService service = service(
                scenarios, inventory, sales, orders, shipmentMapper, outbound,
                actions);
        List<CtAction> created = service.apply(1L);

        assertEquals(1, created.size());
        assertEquals("WMS_REPLENISH", created.get(0).getType());
        assertEquals("WH-1/SKU001", created.get(0).getTargetKey());
        assertEquals("WH-1", readParams(created.get(0)).get("warehouseCode"));
        assertEquals("SKU001", readParams(created.get(0)).get("sku"));
        assertEquals(new BigDecimal("3.5"),
                new BigDecimal(String.valueOf(readParams(created.get(0))
                        .get("qty"))));
    }

    private SandboxService service(
            CtScenarioMapper scenarios,
            InventorySnapshotMapper inventory,
            SalesDailyMapper sales,
            OrderSnapshotMapper orders,
            ShipmentSnapshotMapper shipments,
            WmsOrderSnapshotMapper outbound,
            ActionService actions) {
        return new SandboxService(
                scenarios, inventory, sales, orders, shipments, outbound,
                new SandboxEngine(), actions, mock(CodeGenerator.class),
                new ObjectMapper());
    }

    private CtScenario scenario(String params, String result) {
        CtScenario scenario = new CtScenario();
        scenario.setId(1L);
        scenario.setParamsJson(params);
        scenario.setResultJson(result);
        return scenario;
    }

    private CtAction action(Object requestValue) {
        Map<?, ?> request = (Map<?, ?>) requestValue;
        CtAction action = new CtAction();
        action.setType(String.valueOf(request.get("type")));
        action.setTargetKey(String.valueOf(request.get("targetKey")));
        action.setParams(new ObjectMapper().convertValue(
                request.get("params"), Map.class).toString());
        try {
            action.setParamsJson(new ObjectMapper().writeValueAsString(
                    request.get("params")));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return action;
    }

    private Map<String, Object> readParams(CtAction action) {
        try {
            return new ObjectMapper().readValue(action.getParamsJson(), Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<ShipmentSnapshot> shipments(String carrier, int count) {
        List<ShipmentSnapshot> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ShipmentSnapshot shipment = new ShipmentSnapshot();
            shipment.setWaybillCode(carrier + "-" + index);
            shipment.setCarrierCode(carrier);
            shipment.setStatus("IN_TRANSIT");
            result.add(shipment);
        }
        return result;
    }
}
