package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_order_snapshot")
public class OrderSnapshot extends BaseEntity {
    private String orderNo;
    private String sku;
    private String channelCode;
    private String shopCode;
    private String warehouseCode;
    private String province;
    private String city;
    private String status;
    private BigDecimal payAmount;
    private BigDecimal freight;
    private BigDecimal qty;
    private LocalDateTime orderTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime completeTime;
    private String carrierCode;
    private String trackingNo;
    private String wmsOrderNo;
    private String tmsOrderNo;
    private LocalDateTime syncedAt;
}
