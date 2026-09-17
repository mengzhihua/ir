package com.ir.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_setting")
public class CtSetting extends BaseEntity {
    private String code;
    private String value;
}
