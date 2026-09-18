package com.myproject.wmsserver.common.exception;

import com.myproject.wmsserver.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    /** 请求体格式错误（JSON 解析失败） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.fail(CommonErrorCode.REQUEST_BODY_FORMAT_ERROR));
    }

    /** 数据库唯一键冲突：并发下绕过 Service 预检查时的兜底（如 uk_code） */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return ResponseEntity.status(CommonErrorCode.DATA_ALREADY_EXISTS.getHttpStatus())
                .body(Result.fail(CommonErrorCode.DATA_ALREADY_EXISTS));
    }

    /** 兜底：error 级，带堆栈，对外不暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(Result.fail(CommonErrorCode.SYSTEM_ERROR));
    }

}
