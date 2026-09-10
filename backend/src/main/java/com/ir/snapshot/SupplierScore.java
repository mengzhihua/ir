package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_supplier_score")
public class SupplierScore extends BaseEntity {
    private String supplierCode;
    private String period;
    private Integer receiptCount;
    private BigDecimal onTimeRate;
    private BigDecimal qtyAccuracy;
    private BigDecimal qualityRate;
    private BigDecimal avgScore;
    private String grade;
    private LocalDateTime syncedAt;
}
