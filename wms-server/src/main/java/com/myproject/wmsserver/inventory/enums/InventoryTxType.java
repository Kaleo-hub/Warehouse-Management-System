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
    STOCKTAKING_LOSS("盘亏"),

    /**
     * 出库红冲：已发货的单据被反向单冲掉，货退回仓库。
     *
     * 【为什么不复用 PUTAWAY_IN】虽然都是「库存 +n」，业务含义完全不同：
     * 上架是入库流程的收口，红冲是纠错。混在一起以后查流水就分不清
     * 「这批货是正常进来的还是退回来的」。
     */
    OUTBOUND_REVERSE("出库红冲");

    // 【这里没有 RECONCILE_FIX，是刻意的】
    // 对账修正的是「余额」这个派生值，不是一次库存变动 —— 没有货进出仓库。
    // 如果给它也写一条流水，流水的 SUM 就变了，下一次对账会算出新的差异，
    // 永远收敛不了（这条结论是用测试跑出来的，不是拍脑袋）。
    // 修正的审计痕迹走应用日志 + 对账结果，需要更正式的话应该写 audit_log，而不是库存流水。

    private final String description;

    InventoryTxType(String description) {
        this.description = description;
    }
}