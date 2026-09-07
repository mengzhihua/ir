package com.ir.system;

import com.ir.common.BizException;
import com.ir.common.R;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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
        private String username;
        private String password;
    }

    @Data
    public static class LoginResult {
        private String token;
        private User user;
    }

    @Data
    public static class PasswordReq {
        private String password;
    }

    @PostMapping("/login")
    public R<LoginResult> login(@RequestBody LoginReq request) {
        User user = users.find(request.getUsername());
        if (user == null || !UserStore.hash(request.getPassword()).equals(user.getPassword())) {
            throw new BizException("用户名或密码错误");
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
    public R<Void> password(@RequestBody PasswordReq request) {
        if (request == null || request.getPassword() == null
                || request.getPassword().length() < 6) {
            throw new BizException("新密码至少需要 6 位");
        }
        User user = CurrentUser.get();
        user.setPassword(UserStore.hash(request.getPassword()));
        users.save(user);
        return R.ok();
    }
}
