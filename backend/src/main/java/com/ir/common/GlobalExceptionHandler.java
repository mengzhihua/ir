package com.ir.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<Void> biz(BizException e) { return R.fail(1, e.getMessage()); }

    @ExceptionHandler(Exception.class)
    public R<Void> other(Exception e) { return R.fail(1, e.getMessage() == null ? "请求失败" : e.getMessage()); }
}
