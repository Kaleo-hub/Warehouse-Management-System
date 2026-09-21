package com.myproject.wmsserver.common.idempotent;

import com.myproject.wmsserver.common.idempotent.mapper.IdempotentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 幂等记录的后台维护：两件事，一个负责「删旧的」，一个负责「给卡住的收尾」。
 *
 * ========== 一、清理过期记录 ==========
 * 幂等表是「只增」的：每个写请求都插一行。
 * 没有清理的话它会无限长下去 —— 一开始没人注意，直到某天发现
 * 这张表的体积比业务表还大，而里面 99% 是几个月前的过期记录。
 * 过期时间在写记录时就定好了（默认 24 小时），这里只负责删到期的。
 * 分批删（每批 500，一轮最多 20 批）而不是一条 DELETE 清空：
 * 一次删太多会长时间持有锁，把线上的写请求堵住。
 *
 * ========== 二、给「卡住的处理中」收尾 ==========
 * 租约过期只是「允许别人接手」，但如果根本没人再来重试这行会永远显示处理中。
 * 所以再兜一道：租约过期超过 N 分钟（默认 60 分钟）还停在处理中的，
 * 直接标成失败 —— 状态至少是诚实的，运维也看得出「这次没成」。
 *
 * 两个阈值差得很远（租约 30 秒 vs 兜底 60 分钟）是故意的：
 * 刚过期的记录要给用户留足「自己重试」的机会，不能立刻被判死刑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentCleanupJob {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final IdempotentRecordMapper recordMapper;

    @Value("${wms.idempotent.cleanup-enabled:true}")
    private boolean enabled;

    /** 处理中超过这么多分钟还停在原地的，判定为「持锁进程早就没了」 */
    @Value("${wms.idempotent.stale-processing-minutes:60}")
    private int staleProcessingMinutes;

    @Scheduled(
            fixedDelayString = "${wms.idempotent.cleanup-ms:600000}",
            initialDelayString = "${wms.idempotent.cleanup-initial-delay-ms:60000}")
    public void housekeeping() {
        if (!enabled) {
            return;
        }

        failStaleProcessing();
        deleteExpired();
    }

    /**
     * 把「处理中且租约早就过期」的记录标成失败（status=2）。
     * 标成失败之后，带着同一个 key 的请求就会走「允许重试」分支，
     * 而不是永远被 10007 挡在门外。
     */
    private void failStaleProcessing() {
        int deadlineMinutes = Math.max(staleProcessingMinutes, 1);
        int failed = recordMapper.failStaleProcessing(
                LocalDateTime.now().minusMinutes(deadlineMinutes),
                "处理中超时（" + deadlineMinutes + " 分钟内没有完成），判定为进程异常退出，已标记失败以便重试",
                BATCH_SIZE);
        if (failed > 0) {
            log.warn("把 {} 条超时的「处理中」幂等记录标记为失败（阈值 {} 分钟）", failed, deadlineMinutes);
        }
    }

    private void deleteExpired() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = recordMapper.deleteExpired(BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (total > 0) {
            log.info("清理过期幂等记录 {} 条", total);
        }
    }
}