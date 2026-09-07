package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderSnapshot {
    private String orderNo, channelCode, shopCode, warehouseCode, province, city, status;
    private BigDecimal payAmount = BigDecimal.ZERO, freight = BigDecimal.ZERO, qty = BigDecimal.ZERO;
    private LocalDateTime orderTime, payTime, shipTime, completeTime, syncedAt;
    private String carrierCode, trackingNo, wmsOrderNo, tmsOrderNo;
}
