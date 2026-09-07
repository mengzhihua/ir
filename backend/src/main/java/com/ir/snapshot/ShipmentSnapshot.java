package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShipmentSnapshot {
    private String waybillCode, sourceNo, carrierCode, status, fromSiteCode;
    private LocalDateTime plannedArriveTime, actualArriveTime, syncedAt;
    private BigDecimal freightAmount = BigDecimal.ZERO;
    private boolean exceptionFlag;
}
