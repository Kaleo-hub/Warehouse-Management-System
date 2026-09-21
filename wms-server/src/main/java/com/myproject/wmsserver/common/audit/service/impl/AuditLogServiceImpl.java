package com.myproject.wmsserver.common.audit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.common.audit.dto.AuditLogQuery;
import com.myproject.wmsserver.common.audit.mapper.AuditLogMapper;
import com.myproject.wmsserver.common.audit.service.AuditLogService;
import com.myproject.wmsserver.common.audit.vo.AuditLogVO;
import com.myproject.wmsserver.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 审计日志查询。只读，不加事务 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogVO> page(AuditLogQuery query) {
        Page<AuditLogVO> page = new Page<>(query.getPage(), query.getSize());
        auditLogMapper.selectAuditPage(page, query);
        return PageResult.of(page, page.getRecords());
    }
}