package com.ir.sandbox;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_scenario")
public class CtScenario extends BaseEntity {
    private String scenarioNo;
    private String name;
    private Boolean baseline;
    private String paramsJson;
    private String resultJson;
    private String status;
    private BigDecimal totalCost;
    private BigDecimal serviceLevel;
}
