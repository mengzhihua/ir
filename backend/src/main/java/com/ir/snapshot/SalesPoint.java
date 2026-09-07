package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesPoint {
    private LocalDate salesDate;
    private String sku, warehouseCode, channelCode;
    private BigDecimal qty = BigDecimal.ZERO, amount = BigDecimal.ZERO;
}
