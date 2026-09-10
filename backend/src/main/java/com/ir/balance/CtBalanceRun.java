package com.ir.balance;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_balance_run")
public class CtBalanceRun extends BaseEntity {
    private String runNo;
    private String triggerType;
    private String mode;
    private String status;
    private BigDecimal scoreBefore;
    private BigDecimal scoreAfter;
    private Integer decisionCount;
    private Integer executedCount;
    private Integer pendingCount;
    private String summaryJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
