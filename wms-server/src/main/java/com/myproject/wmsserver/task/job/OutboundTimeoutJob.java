package com.myproject.wmsserver.task.job;

import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.task.service.StockTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时释放：把「已经锁了库存、但一直没人去拣货」的出库单自动放掉（BR-010）。
 *
 * ========== 要解决什么问题 ==========
 * 出库单分配之后库存就被 locked_qty 占住了（货还在货架上，但别人用不了）。
 * 如果这单一直没人处理，那批货就被永久锁死 —— 别人想用也用不了，
 * 看板上还显示"有货"。这类"锁死"是最难被发现的问题：没有任何报错，就是货动不了。
 *
 * ========== 为什么放在 task 模块 ==========
 * 这个动作要同时碰两张表：作废拣货任务（task 模块自己的表）+ 释放出库单（outbound 模块）。
 * docs/02 定的依赖方向是 task → outbound，所以编排放在 task 这边，
 * 出库模块只暴露 listTimeoutAllocatedOrderIds（读）和 release（写自己的表）。
 *
 * ========== 为什么每条单据单独 try-catch ==========
 * 一张单释放失败（比如期间已经被别人推进到 PICKING）不该让整批扫描中断。
 * 定时任务的正确姿势是「能做多少做多少，失败的记下来下一轮再说」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundTimeoutJob {

    /** 系统身份：定时任务没有登录用户，但库存流水要记清楚是谁干的 */
    private static final UserContext.CurrentUser SYSTEM =
            new UserContext.CurrentUser(null, "系统（超时释放）", "SYSTEM");

    private final OutboundOrderService outboundOrderService;
    private final StockTaskService stockTaskService;

    /** 分配后多少分钟还没动就释放 */
    @Value("${wms.outbound.allocated-timeout-minutes:30}")
    private int timeoutMinutes;

    /** 单次最多处理多少张单，避免一轮里锁太多行 */
    @Value("${wms.outbound.timeout-batch-size:50}")
    private int batchSize;

    /** 总开关：本地调试、跑压测时可以关掉，免得后台悄悄改数据 */
    @Value("${wms.outbound.timeout-enabled:true}")
    private boolean enabled;

    /**
     * 扫描间隔由配置决定（默认 60 秒）。
     * fixedDelayString 用表达式读配置 —— 改成 10 秒就能快速自测，不用改代码。
     */
    @Scheduled(
            fixedDelayString = "${wms.outbound.timeout-scan-ms:60000}",
            initialDelayString = "${wms.outbound.timeout-initial-delay-ms:20000}")
    public void releaseTimeoutAllocations() {
        if (!enabled) {
            return;
        }

        List<Long> orderIds = outboundOrderService.listTimeoutAllocatedOrderIds(timeoutMinutes, batchSize);
        if (orderIds.isEmpty()) {
            // 绝大多数轮次都是空跑，不打日志 —— 否则每 60 秒刷一条，真出问题反而找不到
            return;
        }

        log.warn("发现 {} 张已分配超时的出库单（阈值 {} 分钟），开始自动释放",
                orderIds.size(), timeoutMinutes);

        int released = 0;
        int failed = 0;
        for (Long orderId : orderIds) {
            /*
             * 把当前线程标记成「系统」。
             * 释放会写库存流水，那里面要记 operator_name；
             * 不设的话定时任务线程的 UserContext 是空的，流水里就只剩一个空的操作人。
             *
             * 【必须 finally 清掉】@Scheduled 是线程池复用线程的，
             * 这个 ThreadLocal 不清，下一轮任务（或同一线程上的别的任务）会读到上一个身份。
             */
            try {
                UserContext.set(SYSTEM);
                int cancelled = stockTaskService.cancelUnfinishedPickTasks(
                        orderId, "出库单分配后超时未处理，系统自动释放");
                outboundOrderService.release(orderId);
                released++;
                log.info("超时释放完成 orderId={} 作废拣货任务 {} 条", orderId, cancelled);
            } catch (Exception e) {
                failed++;
                log.warn("超时释放失败 orderId={} 原因={}", orderId, e.getMessage());
            } finally {
                UserContext.clear();
            }
        }

        log.warn("超时释放结束：成功 {} 张，失败 {} 张", released, failed);
    }
}