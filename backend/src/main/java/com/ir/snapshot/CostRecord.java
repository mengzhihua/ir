package com.ir.snapshot;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CostRecord {
    private LocalDate bizDate;
    private String orderNo, warehouseCode, carrierCode, costType, sourceSystem, remark;
    private BigDecimal amount = BigDecimal.ZERO;
}
