package com.myproject.wmsserver.common.exception;

import lombok.Getter;

/**
 * 库存模块错误码（5xxxx / 6xxxx 段，见 docs/02 第 4.3 节）。
 * 全部走 HTTP 200 + code，和项目现有约定一致。
 */
@Getter
public enum InventoryErrorCode implements ErrorCode {

    /** qty 被扣成负数 —— 根本没这么多货 */
    STOCK_NOT_ENOUGH(50001, "库存不足"),

    /** 库存行不存在（applyChange 内部会先建行，正常不该出现，属于兜底） */
    STOCK_NOT_FOUND(50002, "库存记录不存在"),

    /** 不变量校验：locked + frozen > qty，也就是可用数量被扣成负数 */
    AVAILABLE_NOT_ENOUGH(60003, "可用库存不足"),

    /** 原子上锁失败：行被别人改过 */
    INVENTORY_CONCURRENT_MODIFY(50005, "库存被并发修改，请重试"),

    /** 一次调用三个变动量全是 0，没有意义的调用 */
    INVALID_QTY_CHANGE(50006, "库存变动数量不能全为 0");

    private final int code;
    private final String message;

    InventoryErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}