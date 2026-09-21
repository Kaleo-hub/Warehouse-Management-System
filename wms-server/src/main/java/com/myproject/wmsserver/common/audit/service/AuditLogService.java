package com.myproject.wmsserver.common.audit.service;

import com.myproject.wmsserver.common.audit.dto.AuditLogQuery;
import com.myproject.wmsserver.common.audit.vo.AuditLogVO;
import com.myproject.wmsserver.common.result.PageResult;

public interface AuditLogService {

    /** 审计日志分页。只读，权限卡在 Controller 上（仅 ADMIN） */
    PageResult<AuditLogVO> page(AuditLogQuery query);
}