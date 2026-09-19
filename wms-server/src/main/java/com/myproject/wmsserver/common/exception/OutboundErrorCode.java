package com.myproject.wmsserver.common.exception;

import lombok.Getter;

/** 出库模块错误码（6xxxx 段） */
@Getter
public enum OutboundErrorCode implements ErrorCode {

    /** 状态机不允许该操作 */
    STATUS_NOT_ALLOWED(60001, "出库单状态不允许该操作"),

    /** FIFO 分配时可用库存不够，整单失败 */
    STOCK_NOT_ENOUGH_FOR_ALLOCATE(60003, "可用库存不足，分配失败"),

    /** 还有没做完的拣货任务时，不能释放分配、不能取消 */
    PICK_TASK_UNFINISHED(60005, "存在未完成的拣货任务"),

    /** 没有 LOCKED 状态的分配记录，生成不出拣货任务 */
    NO_ALLOCATION_TO_PICK(60006, "没有可拣货的分配记录"),

    /** 分配记录状态不对（比如已经拣过货又想释放） */
    ALLOCATE_STATUS_NOT_ALLOWED(60007, "分配记录状态不允许该操作"),

    /** 发货数量与拣货数量对不上 */
    SHIP_QTY_MISMATCH(60009, "发货数量与拣货数量不一致");

    private final int code;
    private final String message;

    OutboundErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}