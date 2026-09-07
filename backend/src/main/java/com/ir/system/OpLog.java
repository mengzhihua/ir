package com.ir.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_op_log")
public class OpLog extends BaseEntity {
    private String operator;
    private String module;
    private String action;
    private String target;
    private String detail;
}
