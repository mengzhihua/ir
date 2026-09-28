package com.ir.system.controller;

import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ir.common.BizException;
import com.ir.common.R;
import com.ir.system.auth.CurrentUser;
import com.ir.system.entity.User;
import com.ir.system.service.TokenService;
import com.ir.system.service.UserStore;
import java.time.LocalDateTime;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserStore users;
    private final TokenService tokens;

    public AuthController(UserStore users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResult {
        private String token;
        private User user;
    }

    @Data
    public static class PasswordReq {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "新密码至少需要 6 位")
        private String newPassword;
    }

    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginReq request) {
        User user = users.find(request.getUsername());
        if (user == null || !UserStore.verify(request.getPassword(),
                user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (UserStore.isLegacy(user.getPassword())) {
            user.setPassword(UserStore.hash(request.getPassword()));
        }
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        LoginResult result = new LoginResult();
        result.setToken(tokens.issue(user));
        result.setUser(user);
        return R.ok(result);
    }

    @GetMapping("/me")
    public R<User> me() {
        return R.ok(CurrentUser.get());
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    @PostMapping("/password")
    public R<Void> password(@Valid @RequestBody PasswordReq request) {
        User user = CurrentUser.get();
        if (!UserStore.verify(request.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码错误");
        }
        user.setPassword(UserStore.hash(request.getNewPassword()));
        users.save(user);
        return R.ok();
    }
}
