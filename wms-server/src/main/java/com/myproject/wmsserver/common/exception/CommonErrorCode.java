package com.myproject.wmsserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode implements ErrorCode {
    SYSTEM_ERROR(10001, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAM_INVALID(10002, "参数校验失败", HttpStatus.BAD_REQUEST),
    REQUEST_BODY_FORMAT_ERROR(10003, "请求体格式错误，请检查是否符合 JSON 格式", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(10004, "资源不存在", HttpStatus.NOT_FOUND),
    DATA_ALREADY_EXISTS(10005, "数据已存在"),
    OPTIMISTIC_LOCK_CONFLICT(10006, "数据已被他人修改，请重试"),
    IDEMPOTENT_CONFLICT(10007, "重复请求，请勿重复提交"),
    OPERATION_FORBIDDEN(10008, "当前状态不允许该操作"),
    /** 发号器异常。放通用码而不是业务码 —— 发号器是基础设施，不该依赖任何业务模块 */
    SEQUENCE_GENERATE_FAILED(10009, "单号生成失败，请重试"),
    /** 基础数据模块错误码（3xxxx 段，见开发规范 4.3），模块错误码多了再拆独立枚举 */
    RELATED_DATA_EXISTS(30012, "存在关联数据，无法删除"),
    /** 上架时库位已用容量会超过上限，对应 BR-006 */
    LOCATION_CAPACITY_NOT_ENOUGH(30009, "库位容量不足");

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
