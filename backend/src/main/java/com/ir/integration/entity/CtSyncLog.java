package com.ir.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_sync_log")
public class CtSyncLog extends BaseEntity {
    private String systemCode;
    private String dataType;
    private String status;
    private Integer rows;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
