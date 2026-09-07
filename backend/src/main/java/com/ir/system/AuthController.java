package com.ir.system;

import com.ir.common.BizException;
import com.ir.common.R;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserStore users; private final TokenService tokens;
    public AuthController(UserStore users, TokenService tokens) { this.users = users; this.tokens = tokens; }
    @Data public static class LoginReq { private String username; private String password; }
    @Data public static class LoginResult { private String token; private User user; }
    @PostMapping("/login")
    public R<LoginResult> login(@RequestBody LoginReq req) {
        User u = users.find(req.getUsername());
        if (u == null || !UserStore.hash(req.getPassword()).equals(u.getPassword())) throw new BizException("用户名或密码错误");
        u.setLastLoginAt(LocalDateTime.now());
        LoginResult r = new LoginResult(); r.setToken(tokens.issue(u)); r.setUser(u); return R.ok(r);
    }
    @GetMapping("/me") public R<User> me() { return R.ok(CurrentUser.get()); }
    @PostMapping("/logout") public R<Void> logout() { return R.ok(); }
    @PostMapping("/password") public R<Void> password() { return R.ok(); }
}
