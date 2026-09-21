package com.myproject.wmsserver.common.audit.controller;

import com.myproject.wmsserver.common.audit.dto.AuditLogQuery;
import com.myproject.wmsserver.common.audit.service.AuditLogService;
import com.myproject.wmsserver.common.audit.vo.AuditLogVO;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口。
 *
 * 【为什么只有 ADMIN 能看】审计日志里记着所有模块的写操作和参数，
 * 是最敏感的一张表。能查它的人，等于能看全公司谁在改什么。
 *
 * 【为什么没有写接口】审计日志和库存流水一样只增不改：
 * 写入口只有一个（AuditLogAspect 自动记），人工只能查。
 */
@RestController
@RequestMapping("/api/v1/system/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** 分页查询：keyword（操作人/动作/对象id）、module、result、traceId、时间区间 */
    @GetMapping
    public Result<PageResult<AuditLogVO>> page(@Validated AuditLogQuery query) {
        return Result.success(auditLogService.page(query));
    }
}