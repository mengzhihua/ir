package com.ir.alert.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.ir.common.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_rule")
public class CtRule extends BaseEntity {
    private String code;
    private String name;
    private String type;
    @TableField("params")
    @JsonIgnore
    private String paramsJson;
    private String severity;
    private Boolean enabled;
    private String suggestedAction;

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
}
