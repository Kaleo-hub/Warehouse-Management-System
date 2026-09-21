package com.myproject.wmsserver.common.idempotent;

import com.myproject.wmsserver.common.idempotent.mapper.IdempotentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 幂等记录清理。
 *
 * ========== 为什么必须有这个任务 ==========
 * 幂等表是「只增」的：每个写请求都插一行。
 * 没有清理的话它会无限长下去 —— 一开始没人注意，直到某天发现
 * 这张表的体积比业务表还大，而里面 99% 是几个月前的过期记录。
 *
 * 过期时间在写记录时就定好了（默认 24 小时），这里只负责删到期的。
 * 分批删（每批 500，一轮最多 20 批）而不是一条 DELETE 清空：
 * 一次删太多会长时间持有锁，把线上的写请求堵住。
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

    @Scheduled(
            fixedDelayString = "${wms.idempotent.cleanup-ms:600000}",
            initialDelayString = "${wms.idempotent.cleanup-initial-delay-ms:60000}")
    public void cleanExpired() {
        if (!enabled) {
            return;
        }

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