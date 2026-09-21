package com.myproject.wmsserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(20001, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(20002, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(20003, "无权限访问", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS(20004, "用户名或密码错误"),
    ACCOUNT_DISABLED(20005, "账号已被禁用"),
    /** 不能删除 / 禁用自己 —— 否则下一次就登不进来了 */
    SELF_PROTECTED(20006, "不能删除或禁用当前登录的账号"),
    /** 系统必须至少留一个启用的管理员，否则没人能再进后台 */
    LAST_ADMIN_PROTECTED(20007, "系统至少要保留一个启用的管理员");

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message) {
        this(code, message, HttpStatus.OK);
    }

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
