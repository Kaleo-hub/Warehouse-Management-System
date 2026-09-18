package com.myproject.wmsserver.inventory.enums;

import lombok.Getter;

/**
 * 库存流水类型，取值严格对应 docs/01 第 7 节「库存变动规则」表格。
 * 数据库里存枚举名（PUTAWAY_IN 这种字符串），不是序号。
 */
@Getter
public enum InventoryTxType {

    PUTAWAY_IN("上架入库"),
    ALLOCATE_LOCK("出库分配锁定"),
    ALLOCATE_RELEASE("释放分配"),
    OUTBOUND_OUT("出库发货"),
    FREEZE("冻结"),
    UNFREEZE("解冻"),
    TRANSFER_OUT("移库转出"),
    TRANSFER_IN("移库转入"),
    STOCKTAKING_GAIN("盘盈"),
    STOCKTAKING_LOSS("盘亏");

    private final String description;

    InventoryTxType(String description) {
        this.description = description;
    }
}