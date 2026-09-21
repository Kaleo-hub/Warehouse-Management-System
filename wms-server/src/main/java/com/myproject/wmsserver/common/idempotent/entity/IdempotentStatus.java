package com.myproject.wmsserver.common.idempotent.entity;

/**
 * 幂等记录状态。数值和建表注释一致（0 处理中 / 1 成功 / 2 失败）。
 */
public final class IdempotentStatus {

    /** 处理中：第一个请求正在执行，此时重复请求要拒绝，不能放进去一起跑 */
    public static final int PROCESSING = 0;

    /** 成功：重复请求直接把快照还回去 */
    public static final int SUCCESS = 1;

    /** 失败：允许重试（业务没成功，不该把用户永久挡在门外） */
    public static final int FAILED = 2;

    private IdempotentStatus() {
    }
}