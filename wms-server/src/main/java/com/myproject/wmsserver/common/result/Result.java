package com.myproject.wmsserver.common.result;

import com.myproject.wmsserver.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private static final int CODE_SUCCESS = 0;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = CODE_SUCCESS;
        r.message = "success";
        r.data = data;
        r.traceId = MDC.get("traceId");
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = MDC.get("traceId");
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}