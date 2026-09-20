package com.ir.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.ir.common.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_op_log")
public class OpLog extends BaseEntity {
    private String operator;
    private String module;
    private String action;
    private String target;
    private String detail;
    private Boolean success;
}
