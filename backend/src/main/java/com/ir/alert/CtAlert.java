package com.ir.alert;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_alert")
public class CtAlert extends BaseEntity {
    private String alertNo;
    private String ruleCode;
    private String type;
    private String severity;
    private String targetType;
    private String targetKey;
    private String warehouseCode;
    private String title;
    private String detail;
    private String status;
    private String suggestedAction;
    private Long actionId;
    private LocalDateTime resolvedAt;
}
