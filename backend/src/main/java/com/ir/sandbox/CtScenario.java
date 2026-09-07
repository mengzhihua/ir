package com.ir.sandbox;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
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
    @JsonIgnore
    private String paramsJson;
    @JsonIgnore
    private String resultJson;
    private String status;
    private BigDecimal totalCost;
    private BigDecimal serviceLevel;

    @JsonProperty("params")
    @JsonRawValue
    public String getParams() {
        return paramsJson;
    }

    @JsonProperty("result")
    @JsonRawValue
    public String getResult() {
        return resultJson;
    }
}
