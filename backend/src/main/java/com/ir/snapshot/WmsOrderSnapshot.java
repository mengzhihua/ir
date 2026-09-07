package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_wms_order_snapshot")
public class WmsOrderSnapshot extends BaseEntity {
    private String code;
    private String externalNo;
    private String warehouseCode;
    private String status;
    private BigDecimal totalQty;
    private BigDecimal pickedQty;
    private BigDecimal shippedQty;
    private String carrier;
    private String trackingNo;
    private LocalDateTime packedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime syncedAt;
}
