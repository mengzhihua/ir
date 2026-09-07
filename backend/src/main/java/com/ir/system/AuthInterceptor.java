package com.ir.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.common.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokens;
    private final UserStore users;
    private final ObjectMapper mapper;
    public AuthInterceptor(TokenService tokens, UserStore users, ObjectMapper mapper) {
        this.tokens = tokens; this.users = users; this.mapper = mapper;
    }
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod()) || "/api/auth/login".equals(req.getRequestURI())) return true;
        TokenService.UserPrincipal p = tokens.parse(bearer(req.getHeader("Authorization")));
        User user = p == null ? null : users.get(p.getId());
        if (user == null || !user.isEnabled()) return reject(res, 401, "未登录或登录已过期");
        if (!allows(user, req.getMethod(), req.getRequestURI())) return reject(res, 403, "当前角色无权执行此操作");
        CurrentUser.set(user); return true;
    }
    @Override public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object h, Exception e) { CurrentUser.clear(); }
    private boolean allows(User u, String method, String path) {
        if (User.ADMIN.equals(u.getRole())) return true;
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) return true;
        if (path.startsWith("/api/auth/")) return true;
        return User.PLANNER.equals(u.getRole()) && !path.startsWith("/api/system/");
    }
    private static String bearer(String h) { return h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null; }
    private boolean reject(HttpServletResponse res, int status, String msg) throws Exception {
        res.setStatus(status); res.setContentType(MediaType.APPLICATION_JSON_VALUE); res.setCharacterEncoding("UTF-8");
        res.getWriter().write(mapper.writeValueAsString(R.fail(status, msg))); return false;
    }
}
