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

    /** 库存行不存在（出库类变动不会自动建行，找不到就是没有这批货） */
    STOCK_NOT_FOUND(50002, "库存记录不存在"),

    /** 不变量校验：locked + frozen > qty，也就是可用数量被扣成负数 */
    AVAILABLE_NOT_ENOUGH(60003, "可用库存不足"),

    /** 原子上锁失败：行被别人改过 */
    INVENTORY_CONCURRENT_MODIFY(50005, "库存被并发修改，请重试"),

    /** 一次调用三个变动量全是 0，没有意义的调用 */
    INVALID_QTY_CHANGE(50006, "库存变动数量不能全为 0"),

    /** 调整类操作（冻结 / 解冻 / 移库 / 盘点）入参数量非法 */
    INVALID_ADJUST_QTY(50007, "调整数量必须大于 0"),

    /** 移库时移出和移入填了同一个库位 */
    TRANSFER_SAME_LOCATION(50008, "移出库位和移入库位不能相同"),

    /** 目标库位已停用或冻结，不能移入 */
    LOCATION_DISABLED(50009, "目标库位已停用或冻结，不能移入"),

    /** 解冻数量超过了实际冻结量（比如冻了 10 却要解冻 20） */
    FREEZE_NOT_ENOUGH(50010, "可解冻数量不足"),

    /** 移库限定在同一仓库内；跨仓库要走出库 + 入库单据，不是「挪个位置」 */
    TRANSFER_CROSS_WAREHOUSE(50011, "只能在同一仓库内移库"),

    /** 盘点单还有明细未录入实盘数量，不能过账 */
    STOCKTAKING_ITEM_INCOMPLETE(50012, "还有盘点明细未录入实盘数量"),

    /** 盘点期间库存余额发生变化，旧快照不能直接过账 */
    STOCKTAKING_SNAPSHOT_CHANGED(50013, "盘点期间库存已变化，请刷新后重新盘点");

    private final int code;
    private final String message;

    InventoryErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
