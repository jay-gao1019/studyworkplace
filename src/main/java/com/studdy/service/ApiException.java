package com.studdy.service;

/** 业务异常 → 全局异常处理器输出 {code, message}（docs/02 §4 错误体约定）。 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
