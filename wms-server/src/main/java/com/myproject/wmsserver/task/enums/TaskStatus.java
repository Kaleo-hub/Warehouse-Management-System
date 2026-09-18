package com.myproject.wmsserver.task.enums;

/**
 * 作业任务状态机（docs/01 第 6.3 节）：
 *
 *   CREATED ──领取──> ASSIGNED ──开始──> PROCESSING ──> DONE
 *      ├──取消──> CANCELLED      └──异常──> EXCEPTION
 *      └──超时──> TIMEOUT ──重新入池──> CREATED（只重试一次）
 *
 * 和 InboundStatus 一样，把「当前状态能做什么」写在枚举里，规则只有一处。
 */
public enum TaskStatus {

    /** 在任务池里，等作业员领取 */
    CREATED,

    /** 已被领取，作业员还没开始 */
    ASSIGNED,

    /** 作业中 */
    PROCESSING,

    /** 已完成 */
    DONE,

    CANCELLED,

    TIMEOUT,

    EXCEPTION;

    public boolean canAssign() {
        return this == CREATED;
    }

    public boolean canStart() {
        return this == ASSIGNED;
    }

    public boolean canComplete() {
        return this == PROCESSING;
    }

    /** 已经结束的状态，不再参与任务池 */
    public boolean isFinished() {
        return this == DONE || this == CANCELLED;
    }
}