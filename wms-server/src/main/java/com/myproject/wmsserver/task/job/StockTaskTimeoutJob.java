package com.myproject.wmsserver.task.job;

import com.myproject.wmsserver.task.mapper.StockTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 任务级超时（docs/01 第 6.3 节的状态机）：
 *
 *   CREATED / ASSIGNED / PROCESSING ──超时──> TIMEOUT ──重新入池──> CREATED（只重试一次）
 *                                                          └── 重试过就停在 TIMEOUT
 *
 * ========== 和 BR-010（出库单超时释放）的区别 ==========
 *   BR-010 处理的是【单据】：分配后一直没人处理 → 把锁定的库存还回去、单据回 CREATED。
 *   这里处理的是【任务】：某个作业员领了活但没干完 / 没人领 → 任务回任务池让别人接手。
 * 两者都做，一条出库单才不会「锁着库存又没人干活」。
 *
 * ========== 为什么先标 TIMEOUT 再回池 ==========
 * 因为它们是两个决策：超时是事实，回池是处置。中间那个 TIMEOUT 状态代表
 * 「确实超时过」；重试次数用完（retry_count >= 1）之后任务就停在那里，
 * 不会再被扫回来 —— 反复重试只会把问题藏起来，该让人看见了。
 *
 * ========== 为什么注入的是自己的 Mapper ==========
 * stock_task 就是本模块的表，直接调 Mapper 是正常的；
 * docs/02 那条「跨模块只能调 Service」约束的是别人家的表（本类没有碰）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockTaskTimeoutJob {

    private static final int BATCH_SIZE = 100;

    private final StockTaskMapper taskMapper;

    @Value("${wms.task.timeout-enabled:true}")
    private boolean enabled;

    /** 重新入池后给的新时限（分钟）。和创建时的时限分开配，避免「刚回池又超时」 */
    @Value("${wms.task.requeue-deadline-minutes:60}")
    private int requeueDeadlineMinutes;

    @Scheduled(
            fixedDelayString = "${wms.task.timeout-scan-ms:60000}",
            initialDelayString = "${wms.task.timeout-initial-delay-ms:30000}")
    public void handleTimeoutTasks() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // ① 超时打标（只碰还没结束的任务）
        int marked = taskMapper.markTimeout(now, BATCH_SIZE);

        // ② 给了新时限再放回任务池：只给一次机会
        int requeued = taskMapper.requeueTimeout(
                now.plusMinutes(Math.max(1, requeueDeadlineMinutes)), BATCH_SIZE);

        if (marked > 0 || requeued > 0) {
            log.warn("任务超时处理：新标记超时 {} 条，重新入池 {} 条", marked, requeued);
        }
    }
}