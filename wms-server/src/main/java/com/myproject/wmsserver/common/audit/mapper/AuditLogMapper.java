package com.myproject.wmsserver.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.common.audit.dto.AuditLogQuery;
import com.myproject.wmsserver.common.audit.entity.AuditLogRecord;
import com.myproject.wmsserver.common.audit.vo.AuditLogVO;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志只需要写入和人工查询，BaseMapper 的 insert 够用。
 * 这里刻意不提供 update / delete 的业务方法 —— 审计日志和库存流水一样，只增不改。
 */
public interface AuditLogMapper extends BaseMapper<AuditLogRecord> {

    /**
     * 审计日志分页。
     *
     * 【为什么按 id 倒序而不是 create_time】审计日志是只增的，id 就是时间序，
     * 而且主键索引比 idx_user_time 更紧凑 —— 看最新的日志不需要额外排序。
     */
    IPage<AuditLogVO> selectAuditPage(IPage<AuditLogVO> page, @Param("query") AuditLogQuery query);
}