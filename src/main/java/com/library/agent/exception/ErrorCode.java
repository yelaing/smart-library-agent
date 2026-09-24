package com.library.agent.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。前三位与 HTTP 状态码对齐，便于日志和前端识别。
 */
public enum ErrorCode {

    /** 请求参数不合法（消息为空、字段缺失等）。 */
    INVALID_REQUEST(40001, HttpStatus.BAD_REQUEST),

    /** 请求路径不存在。 */
    NOT_FOUND(40401, HttpStatus.NOT_FOUND),

    /** HTTP 方法不被该路径支持（例如用 GET 调用只接受 POST 的接口）。 */
    METHOD_NOT_ALLOWED(40501, HttpStatus.METHOD_NOT_ALLOWED),

    /** 请求体 Content-Type 不被支持。 */
    UNSUPPORTED_MEDIA_TYPE(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE),

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
