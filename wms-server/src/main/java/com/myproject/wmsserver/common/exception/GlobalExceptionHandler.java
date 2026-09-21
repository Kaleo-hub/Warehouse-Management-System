package com.myproject.wmsserver.common.exception;

import com.myproject.wmsserver.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    /**
     * 权限不足：@PreAuthorize 拦下来的请求。
     *
     * 【为什么统一转 Result】Spring Security 默认会返回它自己那套 403 页面 / JSON，
     * 和项目「业务错误看响应体里的 code」的约定不一致，前端还得多写一套解析。
     * 这里接住它，转成和其他错误一样的结构（HTTP 403 + code 20003）。
     *
     * 【为什么不用 log.error】权限不够是正常的业务事件（作业员点了主管才能干的按钮），
     * 不是系统故障，warn 就够了，堆栈也不用打。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(AuthErrorCode.FORBIDDEN.getHttpStatus())
                .body(Result.fail(AuthErrorCode.FORBIDDEN));
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

    /** 路径变量 / 查询参数类型错误，例如 /orders/abc，应该按参数错误返回而不是 500。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误 name={} value={}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(Result.fail(CommonErrorCode.PARAM_INVALID));
    }

    /** 不存在的接口 / 静态资源返回 404，别落到系统异常 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(Result.fail(CommonErrorCode.RESOURCE_NOT_FOUND));
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
