package com.ir.balance;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_balance_decision")
public class CtBalanceDecision extends BaseEntity {
    private Long runId;
    private String strategy;
    private String objectiveCode;
    private String targetSystem;
    private String actionType;
    private String targetKey;
    private String paramsJson;
    private BigDecimal expectedCostDelta;
    private BigDecimal expectedNpsDelta;
    private String riskLevel;
    private Boolean approvalRequired;
    private String status;
    private Long actionId;
    private String reason;
    private String decidedBy;
    private LocalDateTime decidedAt;
}
