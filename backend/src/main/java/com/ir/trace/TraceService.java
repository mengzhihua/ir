package com.ir.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.action.CtAction;
import com.ir.action.CtActionMapper;
import com.ir.alert.CtAlert;
import com.ir.alert.CtAlertMapper;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceService {
    private final OrderSnapshotMapper orderMapper;
    private final WmsOrderSnapshotMapper wmsMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final CostRecordMapper costMapper;
    private final CtAlertMapper alertMapper;
    private final CtActionMapper actionMapper;

    public TraceService(
            OrderSnapshotMapper orderMapper,
            WmsOrderSnapshotMapper wmsMapper,
            ShipmentSnapshotMapper shipmentMapper,
            CostRecordMapper costMapper,
            CtAlertMapper alertMapper,
            CtActionMapper actionMapper) {
        this.orderMapper = orderMapper;
        this.wmsMapper = wmsMapper;
        this.shipmentMapper = shipmentMapper;
        this.costMapper = costMapper;
        this.alertMapper = alertMapper;
        this.actionMapper = actionMapper;
    }

    public Page<Map<String, Object>> page(
            String keyword,
            String status,
            String warehouseCode,
            String carrierCode,
            Boolean stuck,
            long current,
            long size) {
        LambdaQueryWrapper<OrderSnapshot> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.like(OrderSnapshot::getOrderNo, keyword.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            query.eq(OrderSnapshot::getStatus, status);
        }
        if (warehouseCode != null && !warehouseCode.trim().isEmpty()) {
            query.eq(OrderSnapshot::getWarehouseCode, warehouseCode);
        }
        if (carrierCode != null && !carrierCode.trim().isEmpty()) {
            query.eq(OrderSnapshot::getCarrierCode, carrierCode);
        }
        query.orderByDesc(OrderSnapshot::getOrderTime);
        Page<OrderSnapshot> orders = orderMapper.selectPage(
                new Page<>(current, size), query);
        Page<Map<String, Object>> resultPage = new Page<>(
                current, size, orders.getTotal());
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderSnapshot order : orders.getRecords()) {
            WmsOrderSnapshot wms = wms(order.getOrderNo());
            ShipmentSnapshot shipment = shipment(order.getOrderNo());
            long stuckHours = stuckHours(order, wms, shipment);
            if (stuck != null && stuck != (stuckHours > 0)) {
                continue;
            }
            result.add(row(order, wms, shipment, stuckHours));
        }
        resultPage.setRecords(result);
        return resultPage;
    }

    public Map<String, Object> detail(String orderNo) {
        OrderSnapshot order = orderMapper.selectOne(new LambdaQueryWrapper<OrderSnapshot>()
                .eq(OrderSnapshot::getOrderNo, orderNo));
        if (order == null) {
            return null;
        }
        WmsOrderSnapshot wms = wms(orderNo);
        ShipmentSnapshot shipment = shipment(orderNo);
        Map<String, Object> result = row(
                order, wms, shipment, stuckHours(order, wms, shipment));
        result.put("timeline", timeline(order, wms, shipment));
        result.put("costs", costMapper.selectList(new LambdaQueryWrapper<CostRecord>()
                .eq(CostRecord::getOrderNo, orderNo)
                .orderByAsc(CostRecord::getBizDate)));
        result.put("alerts", alertMapper.selectList(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getTargetKey, orderNo)));
        result.put("actions", actionMapper.selectList(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getTargetKey, orderNo)));
        return result;
    }

    private Map<String, Object> row(
            OrderSnapshot order,
            WmsOrderSnapshot wms,
            ShipmentSnapshot shipment,
            long stuckHours) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", order.getOrderNo());
        row.put("oms", order);
        row.put("wms", wms);
        row.put("tms", shipment);
        row.put("stage", stage(order, wms, shipment));
        row.put("stuckHours", stuckHours);
        BigDecimal total = BigDecimal.ZERO;
        for (CostRecord cost : costMapper.selectList(new LambdaQueryWrapper<CostRecord>()
                .eq(CostRecord::getOrderNo, order.getOrderNo()))) {
            total = total.add(cost.getAmount());
        }
        row.put("costTotal", total);
        return row;
    }

    private List<Map<String, Object>> timeline(
            OrderSnapshot order,
            WmsOrderSnapshot wms,
            ShipmentSnapshot shipment) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(node("OMS", "ORDER", order.getOrderTime(),
                order.getStatus(), order.getOrderNo()));
        if (wms != null) {
            result.add(node("WMS", "OUTBOUND", wms.getPackedAt(),
                    wms.getStatus(), wms.getCode()));
        }
        if (shipment != null) {
            LocalDateTime time = shipment.getActualArriveTime() == null
                    ? shipment.getPlannedArriveTime()
                    : shipment.getActualArriveTime();
            result.add(node("TMS", "WAYBILL", time,
                    shipment.getStatus(), shipment.getWaybillCode()));
        }
        return result;
    }

    private WmsOrderSnapshot wms(String orderNo) {
        return wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                .eq(WmsOrderSnapshot::getExternalNo, orderNo));
    }

    private ShipmentSnapshot shipment(String orderNo) {
        return shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentSnapshot>()
                .eq(ShipmentSnapshot::getSourceNo, orderNo));
    }

    private String stage(
            OrderSnapshot order,
            WmsOrderSnapshot wms,
            ShipmentSnapshot shipment) {
        if ("CANCELLED".equals(order.getStatus())) {
            return "CANCELLED";
        }
        if (shipment != null && Arrays.asList("DELIVERED", "CLOSED")
                .contains(shipment.getStatus())) {
            return "DELIVERED";
        }
        if (shipment != null) {
            return "TRANSPORT";
        }
        if (wms != null) {
            return "WAREHOUSE";
        }
        return "ORDER";
    }

    private long stuckHours(
            OrderSnapshot order,
            WmsOrderSnapshot wms,
            ShipmentSnapshot shipment) {
        LocalDateTime now = LocalDateTime.now();
        if ("AUDITED".equals(order.getStatus())) {
            return Math.max(0, Duration.between(order.getOrderTime(), now)
                    .toHours() - 4);
        }
        if (wms != null && "PICKING".equals(wms.getStatus())) {
            return Math.max(0, Duration.between(order.getOrderTime(), now)
                    .toHours() - 6);
        }
        if (shipment != null && shipment.getPlannedArriveTime() != null
                && shipment.getPlannedArriveTime().isBefore(now)
                && !Arrays.asList("DELIVERED", "CLOSED")
                .contains(shipment.getStatus())) {
            return Math.max(1, Duration.between(
                    shipment.getPlannedArriveTime(), now).toHours());
        }
        return 0;
    }

    private Map<String, Object> node(
            String system,
            String node,
            LocalDateTime time,
            String status,
            String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("system", system);
        result.put("node", node);
        result.put("time", time);
        result.put("status", status);
        result.put("detail", detail);
        return result;
    }
}
