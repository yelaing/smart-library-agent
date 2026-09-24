package com.library.agent.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。前三位与 HTTP 状态码对齐，便于日志和前端识别。
 */
public enum ErrorCode {

    /** 请求参数不合法（消息为空、字段缺失等）。 */
    INVALID_REQUEST(40001, HttpStatus.BAD_REQUEST),

    /** Agent 单次调用超过 {@code library.chat.timeout} 上限。 */
    AGENT_TIMEOUT(50401, HttpStatus.GATEWAY_TIMEOUT),

    /** 未预期的服务端异常，对外统一话术，细节只进日志。 */
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus status;

    ErrorCode(int code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
