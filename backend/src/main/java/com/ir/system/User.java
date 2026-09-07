package com.ir.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_user")
public class User extends BaseEntity {
    public static final String ADMIN = "ADMIN";
    public static final String PLANNER = "PLANNER";
    public static final String VIEWER = "VIEWER";
    private String username;
    private String password;
    private String realName;
    private String role;
    private Boolean enabled;
    private LocalDateTime lastLoginAt;
}
