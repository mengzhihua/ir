package com.ir.snapshot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.ir.common.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_ext_snapshot")
public class ExtSnapshot extends BaseEntity {
    private String sourceSystem;
    private String dataType;
    private String bizKey;
    private String status;
    private String sku;
    private BigDecimal qty;
    private BigDecimal amount;
    private String plantCode;
    private String title;
    private String extraJson;
    private LocalDateTime syncedAt;
}
