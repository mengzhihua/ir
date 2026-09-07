package com.ir.system;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class OperationLogInterceptor implements HandlerInterceptor {
    private final OpLogMapper mapper;

    public OperationLogInterceptor(OpLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if ("GET".equalsIgnoreCase(request.getMethod())
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")) {
            return;
        }
        User user = CurrentUser.get();
        if (user == null) {
            return;
        }
        OpLog log = new OpLog();
        log.setOperator(user.getUsername());
        String[] segments = request.getRequestURI().split("/");
        log.setModule(segments.length > 2 ? segments[2] : "");
        log.setAction(request.getMethod());
        log.setTarget(request.getRequestURI());
        log.setDetail("HTTP " + response.getStatus());
        mapper.insert(log);
    }
}
