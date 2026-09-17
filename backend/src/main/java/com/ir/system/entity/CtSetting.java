package com.ir.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.ir.common.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_setting")
public class CtSetting extends BaseEntity {
    private String code;
    private String settingValue;
}
