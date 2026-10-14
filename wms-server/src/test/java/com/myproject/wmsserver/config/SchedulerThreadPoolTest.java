package com.myproject.wmsserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时任务线程池的验收测试。
 *
 * ========== 为什么要有这个测试 ==========
 * 调度器配错（忘了配、poolSize 写成 1、线程名前缀写错）不会有任何报错：
 * 应用照常启动、Job 照常跑，只是三个 Job 悄悄挤在一根线程上互相阻塞。
 * 这种问题看代码很难发现，只能靠断言把它钉死。
 *
 * ========== 为什么用「提交耗时任务」来验，而不是等真实 Job 触发 ==========
 * 真实 Job 的触发时机由扫描间隔决定，测试要么等几十秒要么改配置，
 * 而且它们绝大多数轮次是空跑 —— 没数据就直接 return，连日志都不打，根本等不到证据。
 * 直接往调度器里丢三个睡 2 秒的任务，两秒内就能拿到确定结论：
 *     并行（poolSize >= 3）→ 总耗时约 2 秒，三根不同线程
 *     串行（poolSize = 1） → 总耗时约 6 秒，线程名全一样
 * 这个耗时对比就是「线程池到底生效没有」的硬证据，比看日志可靠得多。
 *
 * ========== 会不会干扰真实 Job ==========
 * 测试占 3 根线程约 2 秒，池子有 5 根，剩 2 根给 Job；
 * 而且三个 Job 的 initialDelay 都在 20 秒以上，基本撞不上。
 * 真撞上了也只是 Job 那一轮晚几秒，fixedDelay 下个周期照常补上。
 */
@SpringBootTest
class SchedulerThreadPoolTest {

    /** 和项目里 @Scheduled 方法的数量一致：三个 Job */
    private static final int TASK_COUNT = 3;
    private static final long SLEEP_MS = 2000;

    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    @DisplayName("调度器是我们配的线程池，线程数够三个 Job 并行")
    void schedulerShouldBeConfiguredThreadPool() {
        assertTrue(taskScheduler instanceof ThreadPoolTaskScheduler,
                "应用应当用 SchedulingConfig 里配的 ThreadPoolTaskScheduler，"
                        + "实际是 " + taskScheduler.getClass().getName());

        ScheduledThreadPoolExecutor executor =
                ((ThreadPoolTaskScheduler) taskScheduler).getScheduledThreadPoolExecutor();
        assertNotNull(executor, "调度器应当已初始化");

        assertTrue(executor.getCorePoolSize() >= TASK_COUNT,
                "线程数要够 " + TASK_COUNT + " 个 Job 并行，"
                        + "当前 corePoolSize=" + executor.getCorePoolSize());
    }

    @Test
    @DisplayName("★ 三个任务同时跑：总耗时接近单个任务，且落在三根不同线程上")
    void tasksShouldRunInParallel() throws Exception {
        CountDownLatch done = new CountDownLatch(TASK_COUNT);
        Set<String> threadNames = ConcurrentHashMap.newKeySet();

        long start = System.currentTimeMillis();
        for (int i = 0; i < TASK_COUNT; i++) {
            taskScheduler.schedule(() -> {
                try {
                    threadNames.add(Thread.currentThread().getName());
                    Thread.sleep(SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, Instant.now());
        }

        boolean finished = done.await(30, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;

        System.out.println("三个任务耗时 " + cost + " ms，用到的线程 " + threadNames);

        assertTrue(finished, "30 秒内三个任务都应该跑完");

        assertTrue(cost < SLEEP_MS * 2,
                "并行的话总耗时应接近 " + SLEEP_MS + " ms，实测 " + cost
                        + " ms —— 这么久说明任务是排队跑的，线程池没生效");

        assertEquals(TASK_COUNT, threadNames.size(),
                "应当用到 " + TASK_COUNT + " 根不同线程，实测 " + threadNames);

        assertTrue(threadNames.stream().allMatch(n -> n.startsWith("wms-sched-")),
                "线程名应当以 wms-sched- 开头，实测 " + threadNames);
    }
}
