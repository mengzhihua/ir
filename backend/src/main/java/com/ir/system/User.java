package com.ir.system;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    public static final String ADMIN = "ADMIN";
    public static final String PLANNER = "PLANNER";
    public static final String VIEWER = "VIEWER";
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;
    private boolean enabled = true;
    private LocalDateTime lastLoginAt;
}
