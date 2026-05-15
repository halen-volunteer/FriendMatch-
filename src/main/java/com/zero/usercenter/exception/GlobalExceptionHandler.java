package com.zero.usercenter.exception;

import com.zero.usercenter.DTO.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * 统一把业务异常和系统异常转换成前端可识别的 Result 结构。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常。
     * 这类异常通常由显式校验失败触发，按 warn 级别记录即可。
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getMessage());
    }
    
    /**
     * 处理未预期异常。
     * 对外统一返回兜底文案，避免把堆栈细节暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常，请稍后重试");
    }
}
