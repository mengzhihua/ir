package com.ir.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_system")
public class CtSystem extends BaseEntity {
    private String code;
    private String name;
    private String baseUrl;
    private String authType;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String apiKey;
    private String mode;
    private Boolean enabled;
    private LocalDateTime lastHealthAt;
    private Boolean lastHealthOk;
    private String lastError;
}
