package com.ir.alert;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_rule")
public class CtRule extends BaseEntity {
    private String code;
    private String name;
    private String type;
    private String params;
    private String severity;
    private Boolean enabled;
    private String suggestedAction;
}
