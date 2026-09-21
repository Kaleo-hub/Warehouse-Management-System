package com.myproject.wmsserver.outbound.enums;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;

/**
 * 出库单状态机（docs/01 第 6.2 节）：
 *
 *   CREATED ──分配──> ALLOCATED ──开始拣货──> PICKING ──拣完──> PICKED ──发货──> SHIPPED ──> CLOSED
 *      │                  │
 *      └──取消──> CANCELLED └──释放/超时──> CREATED
 *
 * 【PICKING 不能回退到 ALLOCATED】
 * 到了 PICKING 说明货已经被从货架上拿下来了，这时候释放分配会造成账实不符。
 * 只能往前走到 PICKED 再发货，或者走人工处理。
 * 这是整个状态机里唯一一条"单向不可逆"的边，设计上要特别注意。
 */
public enum OutboundStatus {

    /** 已建单，还没分配库存 */
    CREATED,

    /** 库存已锁定，货还在货架上 */
    ALLOCATED,

    /** 正在拣货，部分任务已领取/执行 */
    PICKING,

    /** 全部拣完，等待复核发货 */
    PICKED,

    /** 已发货，库存已核销 */
    SHIPPED,

    /**
     * 已被红冲：这张单发出去的货，已经被一张反向单退回来了。
     *
     * 【为什么不改成 CANCELLED】「取消」的含义是「这事没发生过」，
     * 而这张单确实发过货。改成 CANCELLED 会抹掉历史，
     * 所以单独给它一个状态：发生过，但已被反向单冲平。
     */
    REVERSED,

    /** 已关闭（收尾状态） */
    CLOSED,

    CANCELLED;

    /** 只有还没分配的能分配 */
    public boolean canAllocate() {
        return this == CREATED;
    }

    /** 只有已分配、还没开始拣货的能释放（PICKING 之后不能释放） */
    public boolean canRelease() {
        return this == ALLOCATED;
    }

    /** 分配完还没开始拣货的，可以生成拣货任务 */
    public boolean canGeneratePickTasks() {
        return this == ALLOCATED;
    }

    /** 全部拣完才能发货 */
    public boolean canShip() {
        return this == PICKED;
    }

    /** 只有已发货的单能被红冲（BR-011：已发货不可撤销，只能开反向单） */
    public boolean canReverse() {
        return this == SHIPPED;
    }

    /** 只有还没分配、也没拣货的能取消 */
    public boolean canCancel() {
        return this == CREATED;
    }

    public static OutboundStatus of(String name) {
        for (OutboundStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
    }
}