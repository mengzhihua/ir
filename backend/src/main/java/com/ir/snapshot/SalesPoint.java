package com.ir.snapshot;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesPoint {
    private LocalDate salesDate;
    private String sku;
    private String warehouseCode;
    private String channelCode;
    private BigDecimal qty;
    private BigDecimal amount;
}
