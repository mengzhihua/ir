package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_shipment_snapshot")
public class ShipmentSnapshot extends BaseEntity {
    private String waybillCode;
    private String sourceNo;
    private String carrierCode;
    private String status;
    private String fromSiteCode;
    private LocalDateTime plannedArriveTime;
    private LocalDateTime actualArriveTime;
    private BigDecimal freightAmount;
    private Boolean exceptionFlag;
    private LocalDateTime syncedAt;
}
