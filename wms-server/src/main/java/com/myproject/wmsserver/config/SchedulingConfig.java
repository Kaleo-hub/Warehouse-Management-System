package com.myproject.wmsserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 打开定时任务。
 *
 * 【为什么单独一个配置类，不写在启动类上】
 * 启动类上堆的注解越多，「这个应用到底开了哪些能力」越难看出来。
 * 定时任务是个需要被注意的能力（它会在后台悄悄改数据），
 * 单独成类、配上说明，比藏在启动类里好。
 *
 * 【为什么用 @Scheduled 而不是上 MQ 延迟队列】
 * 项目第 8 周才引入 MQ。现阶段用「定时扫描 + 状态机 CAS」完全够用，
 * 而且这条路跑通之后，升级到延迟队列只是把「扫描」换成「消息到期投递」，
 * 业务代码（释放逻辑）一行都不用改 —— 这也是 docs/02 里写的演进路径。
 *
 * 定时任务的三个已知代价（面试会被问）：
 *   1. 有延迟：扫描间隔多长，最坏就晚多久释放
 *   2. 有空转：没有超时单的时候也在扫（用索引 + 只返回命中的行把代价压到最低）
 *   3. 多实例会重复扫：单机没问题；多实例要么加分布式锁，要么改成 MQ 投递（第 8 周）
 *
 * ========== 为什么要自己配线程池（实现 SchedulingConfigurer）==========
 * @EnableScheduling 只负责「发现 @Scheduled 方法」，真正跑任务的是另一个东西（TaskScheduler）。
 * 不指定的话 Spring 给一个 poolSize=1 的默认调度器，线程名固定叫 scheduling-1 ——
 * 三个 Job（出库单超时释放 / 作业任务超时 / 幂等记录清理）全排在这一根线程上轮流跑。
 * 日志里永远只有 scheduling-1、从不出现 scheduling-2，就是这个的指纹。
 *
 * ========== 为什么是 ThreadPoolTaskScheduler 而不是 ThreadPoolTaskExecutor ==========
 * 定时任务要的是「延迟多久再执行」，底层是 ScheduledThreadPoolExecutor + 按触发时间排序的
 * DelayQueue；ThreadPoolTaskExecutor 只有「提交后尽快执行」的普通队列，没有延迟能力。
 * 也正因为它用 DelayQueue，这里没有 queueCapacity 的概念 —— poolSize 就是线程数上限。
 *
 * ========== poolSize 为什么是 5 ==========
 * 三个 Job 用的都是 fixedDelay（上一次【跑完】之后再计时），同一个 Job 不会并发执行自己，
 * 同一时刻最多就是「三个 Job 各一个实例」，并发度上限 = Job 数 = 3；给 5 是留两个余量。
 * 【动手前必查】若有 Job 用 fixedRate（上一次【开始】后计时），放大线程池反而会让它在
 * 一轮没跑完时又启动一轮 —— 同一个 Job 并发执行自己。当前三个 Job 全是 fixedDelay。
 */
@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig implements SchedulingConfigurer {

    /** 调度线程数。默认给 5：够三个 Job 并行 + 留两个余量 */
    @Value("${wms.scheduler.pool-size:5}")
    private int poolSize;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(wmsTaskScheduler());
    }

    /*
     * 【为什么声明成 @Bean，而不是在 configureTasks 里 new 一个】
     * 1. 它是 Spring Bean，生命周期交给容器：不用手动 initialize()，
     *    停机时容器会走 destroy()，setWaitForTasksToCompleteOnShutdown 才真的有机会生效。
     * 2. 能被 @Autowired 拿到 —— 测试和监控才有办法验证「应用实际用的是哪个调度器」。
     *    在 configureTasks 里 new 的话，外面根本取不到，只能靠看日志猜。
     * 3. Spring Boot 的自动配置是 @ConditionalOnMissingBean(TaskScheduler.class)，
     *    这里提供了，Boot 就不会再建一个默认的，池子只有这一个。
     */
    @Bean
    public ThreadPoolTaskScheduler wmsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(poolSize);

        // 线程名前缀：日志里一眼看出这是定时任务线程，而不是 HTTP 请求线程
        scheduler.setThreadNamePrefix("wms-sched-");

        /*
         * 【为什么必须配 errorHandler】
         * 不配的话 Spring 用默认的「记一条日志然后吞掉」，
         * 结果就是 Job 每轮都静默失败，而日志里翻不到一个明确的错误。
         * 定时任务最怕的就是这种「看起来在跑、其实一直失败」。
         * 异常不会中断后续调度 —— fixedDelay 下个周期照常触发，这正是我们要的。
         */
        scheduler.setErrorHandler(
                t -> log.error("定时任务执行异常，本轮跳过，下个周期继续", t));

        // 取消掉的任务立刻从队列里移除，不让已取消的延迟任务白占着队列
        scheduler.setRemoveOnCancelPolicy(true);

        /*
         * 【优雅停机】
         * 默认 shutdown 不等任务跑完 —— 停机瞬间正在删幂等记录、正在释放库存的
         * Job 会被硬生生掐断，留下半截状态。这里给 30 秒让它们跑完。
         */
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);

        return scheduler;
    }
}