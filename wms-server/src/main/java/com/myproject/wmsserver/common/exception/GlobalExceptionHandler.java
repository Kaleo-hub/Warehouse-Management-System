package com.myproject.wmsserver.common.exception;

import com.myproject.wmsserver.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("业务异常 code={} message={}", ec.getCode(), ec.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(Result.fail(ec));
    }

    /** 参数校验失败：把字段级错误放进 data */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("参数校验失败 {}", errors);

        Result<Map<String, String>> result = Result.fail(CommonErrorCode.PARAM_INVALID);
        result.setData(errors);
        return ResponseEntity.badRequest().body(result);
    }

    /** 兜底：error 级，带堆栈，对外不暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(Result.fail(CommonErrorCode.SYSTEM_ERROR));
    }

}
