package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WmsOrderSnapshot {
    private String code, externalNo, warehouseCode, status, carrier, trackingNo;
    private BigDecimal totalQty = BigDecimal.ZERO, pickedQty = BigDecimal.ZERO, shippedQty = BigDecimal.ZERO;
    private LocalDateTime packedAt, shippedAt, syncedAt;
}
