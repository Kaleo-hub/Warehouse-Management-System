package com.myproject.wmsserver.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    int getCode();

    String getMessage();

    /** 默认 HTTP 200，需要区分的错误码自己覆盖 */
    default HttpStatus getHttpStatus() {
        return HttpStatus.OK;
    }

}
