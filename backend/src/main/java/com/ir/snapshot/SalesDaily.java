package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_sales_daily")
public class SalesDaily extends BaseEntity {
    private LocalDate salesDate;
    private String sku;
    private String warehouseCode;
    private String channelCode;
    private BigDecimal qty;
    private BigDecimal amount;
}
