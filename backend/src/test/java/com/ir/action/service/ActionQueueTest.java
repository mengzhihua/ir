package com.ir.action.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.common.CarrierCodes;
import com.ir.cost.service.CostService;
import com.ir.sandbox.service.BalanceAdvisor;
import com.ir.sandbox.service.BalancePolicy;
import com.ir.forecast.service.ForecastService;
import com.ir.snapshot.entity.ExtSnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.mapper.ExtSnapshotMapper;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ActionQueueTest {
    @Autowired
    private ActionService actions;
    @Autowired
    private CtActionMapper actionMapper;
    @Autowired
    private OrderSnapshotMapper orderMapper;
    @Autowired
    private BalancePolicy policy;
    @Autowired
    private ShipmentSnapshotMapper shipmentMapper;
    @Autowired
    private CostService costService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExtSnapshotMapper extMapper;
    @Autowired
    private ForecastService forecasts;

    @Test
    void pendingIsDedupedAndOpposingStanceIsSuperseded() {
        CtAction first = actions.createPending(pending("OMS_AUTO_PROCESS", "SO-QUEUE-1", null));
        CtAction again = actions.createPending(pending("OMS_AUTO_PROCESS", "SO-QUEUE-1", null));
        assertEquals(first.getId(), again.getId());

        actions.createPending(pending("TMS_SWITCH_CARRIER", "WB-QUEUE-SF", "SF"));
        CtAction cheap = actions.createPending(pending("TMS_SWITCH_CARRIER", "WB-QUEUE-SELF", "SELF01"));
        int superseded = actions.supersedeOpposing("COST");
        assertTrue(superseded >= 2);
        assertEquals("SUPERSEDED", actionMapper.selectById(first.getId()).getStatus());
        assertEquals("PENDING", actionMapper.selectById(cheap.getId()).getStatus());

        CtAction executed = actions.executePending(cheap.getId());
        assertEquals("SUCCESS", executed.getStatus());
        CtAction againExecute = actions.executePending(cheap.getId());
        assertEquals("SUCCESS", againExecute.getStatus());
        assertEquals(executed.getExecutedAt(), againExecute.getExecutedAt());
    }

    @Test
    void reusingPendingRefreshesExpectedSaving() {
        Map<String, Object> first = pending("OMS_HOLD", "SO-SAVE-1", null);
        first.put("expectedSaving", 10);
        CtAction created = actions.createPending(first);
        Map<String, Object> again = pending("OMS_HOLD", "SO-SAVE-1", null);
        again.put("expectedSaving", 25);
        CtAction reused = actions.createAndExecute(again);
        assertEquals(created.getId(), reused.getId());
        assertEquals(0, new BigDecimal("25").compareTo(reused.getExpectedSaving()));
        assertEquals("SUCCESS", reused.getStatus());
        CtAction retried = actions.retry(reused.getId());
        assertEquals(0, new BigDecimal("25").compareTo(retried.getExpectedSaving()));
    }

    @Test
    void sameOrderHoldReplacesPrioritizePending() {
        CtAction rush = actions.createPending(pending("OMS_PRIORITIZE", "SO-QUEUE-2", null));
        CtAction hold = actions.createPending(pending("OMS_HOLD", "SO-QUEUE-2", null));
        assertEquals("SUPERSEDED", actionMapper.selectById(rush.getId()).getStatus());
        assertEquals("PENDING", hold.getStatus());
        assertNotEquals(rush.getId(), hold.getId());
    }

    @Test
    void switchCarrierRewritesFreightAndSavingActual() {
        ShipmentSnapshot shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<ShipmentSnapshot>()
                        .in(ShipmentSnapshot::getCarrierCode, Arrays.asList("SF", "JD"))
                        .gt(ShipmentSnapshot::getFreightAmount, BigDecimal.ZERO)
                        .last("LIMIT 1"));
        assertNotNull(shipment);
        String fromCarrier = shipment.getCarrierCode();
        BigDecimal fromFreight = shipment.getFreightAmount();
        BigDecimal expectedFreight = CarrierCodes.scaledFreight(
                fromCarrier, "SELF01", fromFreight);
        BigDecimal expectedSaving = BalanceAdvisor.freightSaving(
                fromCarrier, "SELF01", fromFreight);
        Map<String, Object> request = pending(
                "TMS_SWITCH_CARRIER", shipment.getWaybillCode(), "SELF01");
        request.put("expectedSaving", expectedSaving);
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        assertTrue(action.getParamsJson().contains("actualSaving"));
        assertTrue(action.getParamsJson().contains("fromCarrierCode"));
        ShipmentSnapshot updated = shipmentMapper.selectById(shipment.getId());
        assertEquals("SELF01", updated.getCarrierCode());
        assertEquals(0, expectedFreight.compareTo(updated.getFreightAmount()));
        assertEquals(0, fromFreight.subtract(expectedFreight).compareTo(
                paramDecimal(action, "actualSaving")));
        Map<String, Object> saving = costService.saving();
        assertNotNull(saving.get("actual"));
        assertNotNull(saving.get("variance"));
        assertEquals(0, expectedSaving.compareTo(action.getExpectedSaving()));
    }

    @Test
    void switchToFasterCarrierRecordsNegativeSaving() {
        ShipmentSnapshot shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<ShipmentSnapshot>()
                        .eq(ShipmentSnapshot::getCarrierCode, "SELF01")
                        .gt(ShipmentSnapshot::getFreightAmount, BigDecimal.ZERO)
                        .last("LIMIT 1"));
        assertNotNull(shipment);
        BigDecimal fromFreight = shipment.getFreightAmount();
        BigDecimal toFreight = CarrierCodes.scaledFreight(
                "SELF01", "SF", fromFreight);
        Map<String, Object> request = pending(
                "TMS_SWITCH_CARRIER", shipment.getWaybillCode(), "SF");
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        BigDecimal actualSaving = paramDecimal(action, "actualSaving");
        assertTrue(actualSaving.compareTo(BigDecimal.ZERO) < 0);
        assertEquals(0, fromFreight.subtract(toFreight).compareTo(actualSaving));
        ShipmentSnapshot updated = shipmentMapper.selectById(shipment.getId());
        assertEquals("SF", updated.getCarrierCode());
        assertEquals(0, toFreight.compareTo(updated.getFreightAmount()));
    }

    @Test
    void syncTrackClearsExceptionAndRefreshesEta() {
        ShipmentSnapshot shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<ShipmentSnapshot>()
                        .eq(ShipmentSnapshot::getExceptionFlag, true)
                        .last("LIMIT 1"));
        assertNotNull(shipment);
        CtAction action = actions.createAndExecute(
                pending("TMS_SYNC_TRACK", shipment.getWaybillCode(), null));
        assertEquals("SUCCESS", action.getStatus());
        ShipmentSnapshot updated = shipmentMapper.selectById(shipment.getId());
        assertEquals("IN_TRANSIT", updated.getStatus());
        assertEquals(Boolean.FALSE, updated.getExceptionFlag());
        assertNotNull(updated.getPlannedArriveTime());
        assertTrue(updated.getPlannedArriveTime().isAfter(LocalDateTime.now()));
        assertNotNull(updated.getSyncedAt());
        assertTrue(action.getParamsJson().contains("fromExceptionFlag"));
    }

    @Test
    void dispatchWritesCreatedWaybillToDispatched() {
        ShipmentSnapshot shipment = new ShipmentSnapshot();
        shipment.setWaybillCode("WB-IR-DISPATCH");
        shipment.setSourceNo("SO-IR-DISPATCH");
        shipment.setCarrierCode("SF");
        shipment.setStatus("CREATED");
        shipment.setFromSiteCode("WH01");
        shipment.setFreightAmount(new BigDecimal("20"));
        shipment.setExceptionFlag(false);
        shipmentMapper.insert(shipment);
        CtAction action = actions.createAndExecute(
                pending("TMS_DISPATCH", "WB-IR-DISPATCH", null));
        assertEquals("SUCCESS", action.getStatus());
        ShipmentSnapshot updated = shipmentMapper.selectById(shipment.getId());
        assertEquals("DISPATCHED", updated.getStatus());
        assertEquals("CREATED", paramText(action, "fromStatus"));
    }

    @Test
    void syncTrackByOrderWritesEveryMatchingWaybill() {
        ShipmentSnapshot first = new ShipmentSnapshot();
        first.setWaybillCode("WB-IR-ORDER-1");
        first.setSourceNo("SO-IR-MULTI");
        first.setCarrierCode("SF");
        first.setStatus("DISPATCHED");
        first.setFromSiteCode("WH01");
        first.setExceptionFlag(true);
        first.setPlannedArriveTime(LocalDateTime.now().minusHours(3));
        first.setFreightAmount(new BigDecimal("18"));
        shipmentMapper.insert(first);
        ShipmentSnapshot second = new ShipmentSnapshot();
        second.setWaybillCode("WB-IR-ORDER-2");
        second.setSourceNo("SO-IR-MULTI");
        second.setCarrierCode("JD");
        second.setStatus("CREATED");
        second.setFromSiteCode("WH02");
        second.setExceptionFlag(true);
        second.setPlannedArriveTime(LocalDateTime.now().minusHours(5));
        second.setFreightAmount(new BigDecimal("22"));
        shipmentMapper.insert(second);
        CtAction action = actions.createAndExecute(
                pending("TMS_SYNC_TRACK", "SO-IR-MULTI", null));
        assertEquals("SUCCESS", action.getStatus());
        ShipmentSnapshot one = shipmentMapper.selectById(first.getId());
        ShipmentSnapshot two = shipmentMapper.selectById(second.getId());
        assertEquals("IN_TRANSIT", one.getStatus());
        assertEquals("IN_TRANSIT", two.getStatus());
        assertEquals(Boolean.FALSE, one.getExceptionFlag());
        assertEquals(Boolean.FALSE, two.getExceptionFlag());
    }

    @Test
    void savingActualIgnoresEstimatedWithoutWriteback() {
        Map<String, Object> before = costService.saving();
        BigDecimal actualBefore = new BigDecimal(String.valueOf(before.get("actual")));
        BigDecimal totalBefore = new BigDecimal(String.valueOf(before.get("total")));
        Map<String, Object> request = pending("OMS_HOLD", "SO-SAVE-UNWRITTEN", null);
        request.put("expectedSaving", 99);
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        Map<String, Object> after = costService.saving();
        assertEquals(0, actualBefore.compareTo(
                new BigDecimal(String.valueOf(after.get("actual")))));
        assertEquals(0, totalBefore.add(new BigDecimal("99")).compareTo(
                new BigDecimal(String.valueOf(after.get("total")))));
    }

    @Test
    void purchaseSuggestWritesOpenPoAndInbound() {
        BigDecimal priorGz = forecasts.inboundOf("SKU005", "WH-GZ");
        BigDecimal priorBj = forecasts.inboundOf("SKU005", "WH-BJ");
        Map<String, Object> request = pending("SRM_PURCHASE_SUGGEST", "SKU005", null);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("sku", "SKU005");
        params.put("qty", 50);
        params.put("warehouseCode", "WH-GZ");
        request.put("params", params);
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        assertTrue(action.getParamsJson().contains("IR-PO-SRM-SKU005-WH-GZ"));
        ExtSnapshot po = extMapper.selectOne(
                new LambdaQueryWrapper<ExtSnapshot>()
                        .eq(ExtSnapshot::getBizKey, "IR-PO-SRM-SKU005-WH-GZ")
                        .last("LIMIT 1"));
        assertNotNull(po);
        assertEquals("OPEN", po.getStatus());
        assertEquals("SKU005", po.getSku());
        assertEquals("WH-GZ", po.getPlantCode());
        assertTrue(po.getExtraJson() != null && po.getExtraJson().contains("WH-GZ"));
        assertEquals(0, priorGz.add(new BigDecimal("50")).compareTo(po.getQty()));
        assertEquals(0, priorGz.add(new BigDecimal("50")).compareTo(
                forecasts.inboundOf("SKU005", "WH-GZ")));
        assertEquals(0, priorBj.compareTo(forecasts.inboundOf("SKU005", "WH-BJ")));
    }

    @Test
    void prioritizeWritesSnapshotPriority() {
        Map<String, Object> request = pending("OMS_PRIORITIZE", "SO000043", null);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("priority", 10);
        request.put("params", params);
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        OrderSnapshot order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, "SO000043"));
        org.junit.jupiter.api.Assertions.assertNotNull(order);
        assertEquals(Integer.valueOf(10), order.getPriority());
    }

    @Test
    void policyUpdateRestoresBalanced() {
        policy.update(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.5));
        assertEquals("BALANCED", policy.stance());
    }

    private Map<String, Object> pending(String type, String targetKey, String carrier) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("type", type);
        request.put("targetKey", targetKey);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        if (carrier != null) {
            params.put("carrierCode", carrier);
        }
        request.put("params", params);
        return request;
    }

    private BigDecimal paramDecimal(CtAction action, String key) {
        return new BigDecimal(paramText(action, key));
    }

    private String paramText(CtAction action, String key) {
        try {
            Map<String, Object> params = objectMapper.readValue(
                    action.getParamsJson(),
                    new TypeReference<Map<String, Object>>() {
                    });
            return String.valueOf(params.get(key));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
