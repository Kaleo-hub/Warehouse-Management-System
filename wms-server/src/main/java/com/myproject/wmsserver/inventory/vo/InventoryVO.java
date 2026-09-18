package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存出参。
 *
 * warehouseName / locationCode / skuCode / skuName 这几个冗余字段是必须的 ——
 * 库存列表要直接显示名称，不能只给一堆 id 让前端再反查（那是 N+1 的另一种形式）。
 */
@Data
public class InventoryVO {

    private Long id;

    private Long warehouseId;

    private String warehouseName;

    private Long locationId;

    private String locationCode;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    /** 在库实物数量 */
    private Integer qty;

    /** 已分配未发货 */
    private Integer lockedQty;

    /** 冻结 */
    private Integer frozenQty;

    /** 可用 = qty - locked - frozen，直接读 MySQL 生成列，应用层不要重算 */
    private Integer availableQty;

    /** 首次入库时间，FIFO 靠它排序 */
    private LocalDateTime inboundTime;
}