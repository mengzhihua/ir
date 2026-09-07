package com.ir.action;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("ct_action")
public class CtAction extends BaseEntity {
    private String actionNo;
    private String type;
    private String targetSystem;
    private String targetKey;
    @TableField("params")
    @JsonIgnore
    private String paramsJson;
    private String status;
    @TableField("result")
    @JsonIgnore
    private String resultJson;
    private Long alertId;
    private String operator;
    private BigDecimal expectedSaving;
    private LocalDateTime executedAt;

    @JsonProperty("params")
    @JsonRawValue
    public String getParams() {
        return paramsJson;
    }

    public void setParams(String value) {
        this.paramsJson = value;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String value) {
        this.paramsJson = value;
    }

    @JsonProperty("result")
    public String getResult() {
        return resultJson;
    }

    public void setResult(String value) {
        this.resultJson = value;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String value) {
        this.resultJson = value;
    }
}
