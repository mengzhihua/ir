package com.ir.objective;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_objective")
public class CtObjective extends BaseEntity {
    private String code;
    private String name;
    private String category;
    private String metric;
    private String direction;
    private BigDecimal targetValue;
    private BigDecimal weight;
    private String unit;
    private Boolean enabled;
}
