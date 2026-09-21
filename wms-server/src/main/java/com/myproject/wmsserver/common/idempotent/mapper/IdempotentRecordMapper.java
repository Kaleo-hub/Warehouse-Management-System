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
                  @Param("expireTime") LocalDateTime expireTime,
                  @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 接手一个「租约已经过期」的处理中记录。
     *
     * 条件里带 status = 0 和租约过期，是【CAS】：并发的两个重试只会有一个改成功
     * （影响行数 1），另一个拿到 0 继续等 —— 不会出现两个人同时接手同一件事。
     *
     * 租约已过期说明原来那个进程已经不在了（或者慢得离谱），
     * 所以接手是安全的；这也是为什么租约要设得远大于正常业务耗时。
     */
    int preempt(@Param("key") String key,
                @Param("requestHash") String requestHash,
                @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 兜底：把「处理中且租约过期很久」的记录标成失败。
     *
     * 为什么要有它：租约过期只是「允许别人接手」，但如果根本没人再来重试，
     * 这行会一直显示处理中 —— 状态是假的。定时把它们标成失败，
     * 至少让人从数据上看出「这次没成，可以重试」。
     *
     * @param deadline 租约早于这个时间点的才算「过期很久」
     */
    int failStaleProcessing(@Param("deadline") LocalDateTime deadline,
                            @Param("errorMsg") String errorMsg,
                            @Param("limit") int limit);

    /** 执行成功：把状态改成 1，并把响应快照存下来供重复请求重放 */
    int markSuccess(@Param("key") String key, @Param("snapshot") String snapshot);

    /** 执行失败：状态改成 2 + 记下原因，下次同一个 key 再来就是允许重试 */
    int markFailed(@Param("key") String key, @Param("errorMsg") String errorMsg);

    /** 上一次失败了，这次放行：把状态改回处理中，并给一份新的租约 */
    int resetToProcessing(@Param("key") String key,
                          @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 清理过期记录。定时任务调用。
     * 用 LIMIT 分批删，避免一次删太多把数据库卡住。
     */
    int deleteExpired(@Param("limit") int limit);
}