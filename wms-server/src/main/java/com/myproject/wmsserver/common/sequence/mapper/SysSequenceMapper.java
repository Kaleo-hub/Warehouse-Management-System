package com.myproject.wmsserver.common.sequence.mapper;

import org.apache.ibatis.annotations.Param;

public interface SysSequenceMapper {

    /**
     * 取下一个序号。
     * INSERT ... ON DUPLICATE KEY UPDATE 会在这一行上加排他锁，
     * 并发调用自动排队，所以拿到的值是连续且不重复的。
     * 返回影响行数（1 或 2），调用方不需要关心。
     */
    int nextValue(@Param("bizType") String bizType, @Param("bizDate") String bizDate);

    /** 读当前值。必须在 nextValue 之后、同一个事务里调用 */
    Long selectCurrentValue(@Param("bizType") String bizType, @Param("bizDate") String bizDate);
}