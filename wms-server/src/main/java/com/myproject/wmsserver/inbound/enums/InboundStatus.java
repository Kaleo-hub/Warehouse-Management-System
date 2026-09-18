package com.myproject.wmsserver.inbound.enums;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.InboundErrorCode;

/**
 * 入库单状态机（docs/01 第 6.1 节）：
 *
 *   CREATED ──收货──> RECEIVING ──收完──> RECEIVED ──上架完成──> PUTAWAY_DONE ──> CLOSED
 *      └──取消──> CANCELLED
 *
 * 只能沿箭头走，禁止跳跃和回退（BR-007）。
 *
 * 把「当前状态能做什么」写在枚举里，好处是规则只有一处：
 * Service 里判断 canReceive()，前端要的也是同一套规则，
 * 不会出现「Service 放行、别处拦住」的不一致。
 */
public enum InboundStatus {

    /** 已建单，还没开始收货 */
    CREATED,

    /** 收了一部分 */
    RECEIVING,

    /** 全部明细都收满了 */
    RECEIVED,

    /** 所有明细都已上架 */
    PUTAWAY_DONE,

    /** 已关闭（收尾状态） */
    CLOSED,

    /** 已取消 */
    CANCELLED;

    /** CREATED 和 RECEIVING 都还能继续收货 */
    public boolean canReceive() {
        return this == CREATED || this == RECEIVING;
    }

    /** 只有还没开始收货的单据能取消 */
    public boolean canCancel() {
        return this == CREATED;
    }

    /** 只有收完货的单据能生成上架任务 */
    public boolean canGeneratePutawayTasks() {
        return this == RECEIVED;
    }

    /** 从数据库里读出来的状态字符串转枚举，不认识的值直接抛业务异常，不要静默放过 */
    public static InboundStatus of(String name) {
        for (InboundStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new BizException(InboundErrorCode.STATUS_NOT_ALLOWED);
    }
}