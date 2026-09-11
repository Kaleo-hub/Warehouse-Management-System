package com.myproject.wmsserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode implements ErrorCode {
    SYSTEM_ERROR(10001, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAM_INVALID(10002, "参数校验失败", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(10004, "资源不存在", HttpStatus.NOT_FOUND),
    DATA_ALREADY_EXISTS(10005, "数据已存在"),
    OPTIMISTIC_LOCK_CONFLICT(10006, "数据已被他人修改，请重试"),
    IDEMPOTENT_CONFLICT(10007, "重复请求，请勿重复提交"),
    OPERATION_FORBIDDEN(10008, "当前状态不允许该操作");

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(int code, String message) {
        this(code, message, HttpStatus.OK);
    }

    CommonErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
