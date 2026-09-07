package com.ir.action;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_action")
public class CtAction extends BaseEntity {
    private String actionNo;
    private String type;
    private String targetSystem;
    private String targetKey;
    private String params;
    private String status;
    private String result;
    private Long alertId;
    private String operator;
    private BigDecimal expectedSaving;
    private LocalDateTime executedAt;
}
