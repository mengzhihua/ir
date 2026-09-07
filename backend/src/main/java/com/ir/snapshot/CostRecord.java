package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_cost_record")
public class CostRecord extends BaseEntity {
    private LocalDate bizDate;
    private String orderNo;
    private String warehouseCode;
    private String carrierCode;
    private String costType;
    private BigDecimal amount;
    private String sourceSystem;
    private String remark;
}
