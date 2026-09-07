package com.ir.forecast;

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
@TableName("ct_forecast")
public class CtForecast extends BaseEntity {
    private String runNo;
    private String sku;
    private String warehouseCode;
    private String method;
    private Integer horizon;
    private BigDecimal mape;
    @JsonIgnore
    private String resultJson;

    @JsonProperty("result")
    @JsonRawValue
    public String getResult() {
        return resultJson;
    }
}
