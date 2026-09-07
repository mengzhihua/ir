package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventorySnapshot {
    private String sourceSystem, warehouseCode, sku;
    private BigDecimal qtyOnHand = BigDecimal.ZERO, qtyReserved = BigDecimal.ZERO, qtyAvailable = BigDecimal.ZERO, safetyQty = BigDecimal.ZERO;
    private LocalDateTime syncedAt;
}
