package com.ir.common;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Release 包把 Vue 构建产物放进同一个 JAR。浏览器刷新 /dashboard 这类前端路由时，
 * 转发到 index.html，不拦截 /api、H2、Swagger。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaForwardFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri == null || uri.isEmpty()) {
            uri = "/";
        }
        if (shouldForward(uri)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldForward(String uri) {
        if (uri.startsWith("/api/") || "/api".equals(uri)) {
            return false;
        }
        if (uri.startsWith("/h2") || uri.startsWith("/swagger")
                || uri.startsWith("/v3/") || uri.startsWith("/actuator")
                || uri.startsWith("/error")) {
            return false;
        }
        int slash = uri.lastIndexOf('/');
        String last = slash >= 0 ? uri.substring(slash + 1) : uri;
        return !last.contains(".");
    }
}
