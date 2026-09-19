package com.myproject.wmsserver.outbound.enums;

/** 出库分配明细状态 */
public enum AllocateStatus {

    /** 已锁定库存，等拣货 */
    LOCKED,

    /** 已拣货，等发货核销 */
    PICKED,

    /** 已释放（库存的 locked_qty 已经还回去） */
    RELEASED
}