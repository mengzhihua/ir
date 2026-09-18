package com.ir.trace.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.ShipmentSnapshot;
import com.ir.snapshot.mapper.ShipmentSnapshotMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TraceServiceTest {
    @Autowired
    private TraceService traces;
    @Autowired
    private ActionService actions;
    @Autowired
    private ShipmentSnapshotMapper shipmentMapper;

    @Test
    void detailIncludesWaybillSwitchAction() {
        ShipmentSnapshot shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<ShipmentSnapshot>()
                        .isNotNull(ShipmentSnapshot::getSourceNo)
                        .isNotNull(ShipmentSnapshot::getWaybillCode)
                        .last("LIMIT 1"));
        assertNotNull(shipment);
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("type", "TMS_SWITCH_CARRIER");
        request.put("targetKey", shipment.getWaybillCode());
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("carrierCode", "SELF01");
        request.put("params", params);
        CtAction created = actions.createAndExecute(request);
        assertNotNull(created);
        Map<String, Object> detail = traces.detail(shipment.getSourceNo());
        assertNotNull(detail);
        @SuppressWarnings("unchecked")
        List<CtAction> rows = (List<CtAction>) detail.get("actions");
        assertNotNull(rows);
        boolean found = false;
        for (CtAction row : rows) {
            if (shipment.getWaybillCode().equals(row.getTargetKey())
                    && "TMS_SWITCH_CARRIER".equals(row.getType())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "订单抽屉应包含运单换商指令");
    }

    @Test
    void stuckFilterReturnsAuditedOrders() {
        Page<Map<String, Object>> page = traces.page(null, null, null, null, true, null, 1, 20);
        assertNotNull(page);
        java.util.List<Map<String, Object>> rows = page.getRecords();
        assertTrue(rows.size() > 0, "stuck=true 应返回卡单而不是空页");
        assertTrue(page.getTotal() >= rows.size());
        boolean stuckRow = false;
        for (Map<String, Object> row : rows) {
            Object hours = row.get("stuckHours");
            assertNotNull(hours);
            if (((Number) hours).longValue() > 0) {
                stuckRow = true;
            }
            Object oms = row.get("oms");
            if (oms instanceof OrderSnapshot && "AUDITED".equals(((OrderSnapshot) oms).getStatus())) {
                stuckRow = true;
            }
        }
        assertTrue(stuckRow);
    }
}
