package com.myproject.wmsserver.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「同一个请求提交多次，效果只能有一次」的接口。
 *
 * 【怎么用】
 *   @Idempotent(bizType = "OUTBOUND_ALLOCATE", key = "#orderId")
 *   public Result<Void> allocate(@PathVariable Long orderId) { ... }
 *
 * 【key 是什么】
 * 业务键的 SpEL 表达式，指向方法参数里那个「这件事作用在谁身上」的值：
 *   发货 → #orderId   冻结 → #id
 * 没有天然的业务键（比如「创建单据」，那时单号还不存在）就留空，
 * 由前端在请求头 X-Idempotent-Key 里带一个 UUID。
 *
 * 【key 的最终形态】bizType + 客户端key/业务键 + 请求体哈希。
 * 带上请求体哈希是有意的：同一个单据的两次「内容不同」的请求是两件事，
 * 比如分批收货——收 30 件和再收 30 件必须都能执行；
 * 而内容完全相同的重复提交才是要挡的。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务类型，写进 idempotent_record.biz_type，便于统计和排查 */
    String bizType();

    /** 业务键的 SpEL 表达式，如 "#orderId"。留空表示只能用请求头里的客户端 key */
    String key() default "";

    /**
     * 记录保留多少分钟。
     * 默认 24 小时够覆盖绝大多数的「用户重试」；
     * 「内容相同但确实要执行两次」的场景（比如收货）可以调短，减少误挡。
     */
    int ttlMinutes() default 1440;
}