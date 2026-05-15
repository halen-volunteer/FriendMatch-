package com.zero.usercenter.exception;

/**
 * 业务异常。
 * 用于承载可预期的业务校验失败，并交给全局异常处理器统一返回。
 */
public class BusinessException extends RuntimeException {
    
    private int code;
    
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
}
