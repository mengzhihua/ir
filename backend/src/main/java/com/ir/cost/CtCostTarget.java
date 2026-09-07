package com.ir.cost;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_cost_target")
public class CtCostTarget extends BaseEntity {
    @TableField("\"month\"")
    private String month;
    private String costType;
    private BigDecimal targetAmount;
}
