package com.myproject.wmsserver.common.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 审计日志查询条件（GET 参数自动绑定） */
@Data
public class AuditLogQuery {

    @Min(value = 1, message = "page 不能小于 1")
    private int page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 最大 100")
    private int size = 20;

    /** 模糊匹配操作人 / 动作 / 对象 id */
    private String keyword;

    /** 模块：inbound / inventory / system ... */
    private String module;

    /** 结果：1 成功 0 失败 */
    private Integer result;

    /** 链路 ID 精确匹配：排查某一次请求时最好用 */
    private String traceId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}