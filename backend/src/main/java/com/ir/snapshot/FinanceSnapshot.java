package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_finance_snapshot")
public class FinanceSnapshot extends BaseEntity {
    private String metric;
    private String dimension;
    private BigDecimal amount;
    private Integer itemCount;
    private LocalDateTime syncedAt;
}
