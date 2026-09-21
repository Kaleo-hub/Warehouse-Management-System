package com.myproject.wmsserver.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.common.audit.entity.AuditLogRecord;

/**
 * 审计日志只需要写入和人工查询，BaseMapper 的 insert 够用。
 * 这里刻意不提供 update / delete 的业务方法 —— 审计日志和库存流水一样，只增不改。
 */
public interface AuditLogMapper extends BaseMapper<AuditLogRecord> {
}