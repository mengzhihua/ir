package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_purchase_snapshot")
public class PurchaseSnapshot extends BaseEntity {
    private String docType;
    private String code;
    private String refCode;
    private String supplierCode;
    private String plantCode;
    private String sku;
    private String status;
    private BigDecimal qty;
    private BigDecimal receivedQty;
    private BigDecimal amount;
    private LocalDate expectedDate;
    private LocalDateTime receivedAt;
    private LocalDateTime syncedAt;
}
