package com.ir.action.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.sandbox.service.BalancePolicy;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.mapper.OrderSnapshotMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void prioritizeWritesSnapshotPriority() {
        Map<String, Object> request = pending("OMS_PRIORITIZE", "SO000043", null);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("priority", 10);
        request.put("params", params);
        CtAction action = actions.createAndExecute(request);
        assertEquals("SUCCESS", action.getStatus());
        OrderSnapshot order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderSnapshot>()
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
}
