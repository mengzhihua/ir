package com.ir.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(
            GlobalExceptionHandler.class);
    @ExceptionHandler(BizException.class)
    public R<Void> biz(BizException e) { return R.fail(1, e.getMessage()); }

    @ExceptionHandler(Exception.class)
    public R<Void> other(Exception e) {
        log.error("未预期的服务器异常", e);
        return R.fail(1, "服务器内部错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> illegal(IllegalArgumentException e) {
        return R.fail(1, e.getMessage());
    }
}
