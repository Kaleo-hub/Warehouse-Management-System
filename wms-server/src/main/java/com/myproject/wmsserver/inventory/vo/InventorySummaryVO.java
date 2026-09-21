package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.util.List;

/**
 * 库存总览（工作台看板用）。
 *
 * 一次请求把看板需要的数字全给出来，避免前端拼好几个接口。
 */
@Data
public class InventorySummaryVO {

    /* ---------------- 库存 ---------------- */

    /** 在库总量 */
    private Long totalQty;

    /** 锁定总量（已分配未发货） */
    private Long lockedQty;

    /** 冻结总量 */
    private Long frozenQty;

    /** 可用总量 */
    private Long availableQty;

    /** 在库 SKU 数（有库存的商品种类） */
    private Integer skuCount;

    /** 有货的库位数 */
    private Integer usedLocationCount;

    /* ---------------- 库位容量 ---------------- */

    /** 启用库位总数 */
    private Integer locationCount;

    /** 容量合计（capacity=0 的不限位不计入） */
    private Long capacityTotal;

    /** 已用容量合计 */
    private Long capacityUsed;

    /** 容量占用率（百分比，保留 1 位小数）。capacityTotal=0 时为 null */
    private Double capacityUsageRate;

    /* ---------------- 待处理 ---------------- */

    /** 待处理作业任务数（CREATED / ASSIGNED / PROCESSING） */
    private Integer pendingTaskCount;

    /** 待收货入库单数 */
    private Integer pendingInboundCount;

    /** 待处理出库单数（未发货的） */
    private Integer pendingOutboundCount;

    /* ---------------- 按仓库分布 ---------------- */

    private List<InventoryGroupVO> byWarehouse;
}