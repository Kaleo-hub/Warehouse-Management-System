package com.myproject.wmsserver.common.idempotent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.common.idempotent.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {

    /**
     * 抢位插入。
     *
     * 【为什么不用「先 insert 再 catch DuplicateKeyException」】
     * 在事务里捕获唯一键异常会把事务标记成 rollback-only，
     * 之后同一事务里再查这条记录就会报错，处理起来很别扭。
     * 用 INSERT ... ON DUPLICATE KEY UPDATE id = id：
     *   插入成功 → 影响行数 1（我是第一个）
     *   已存在   → 影响行数 0（有人先来了，但没有任何异常）
     * 这招和 InventoryMapper.ensureRow 是同一个套路，项目里已经用过。
     *
     * @return 1 表示抢到了（首次请求），0 表示 key 已存在
     */
    int tryInsert(@Param("key") String key,
                  @Param("bizType") String bizType,
                  @Param("requestHash") String requestHash,
                  @Param("expireTime") LocalDateTime expireTime);

    /** 执行成功：把状态改成 1，并把响应快照存下来供重复请求重放 */
    int markSuccess(@Param("key") String key, @Param("snapshot") String snapshot);

    /** 执行失败：状态改成 2 + 记下原因，下次同一个 key 再来就是允许重试 */
    int markFailed(@Param("key") String key, @Param("errorMsg") String errorMsg);

    /** 上一次失败了，这次放行：把状态改回处理中 */
    int resetToProcessing(@Param("key") String key);

    /**
     * 清理过期记录。定时任务调用。
     * 用 LIMIT 分批删，避免一次删太多把数据库卡住。
     */
    int deleteExpired(@Param("limit") int limit);
}