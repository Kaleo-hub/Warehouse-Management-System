package com.myproject.wmsserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}