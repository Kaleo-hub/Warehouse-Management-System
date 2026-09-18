package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 库存流水出参。字段和 inventory_transaction 表一一对应 */
@Data
public class InventoryTransactionVO {

    private Long id;

    private String txNo;

    private Long warehouseId;

    private String warehouseName;

    private Long locationId;

    private String locationCode;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    /** InventoryTxType 枚举名，如 PUTAWAY_IN */
    private String txType;

    private Integer qtyChange;

    private Integer lockedChange;

    private Integer frozenChange;

    /** 变动前后：对账时看相邻流水是否连续就靠这两个字段 */
    private Integer qtyBefore;

    private Integer qtyAfter;

    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long operatorId;

    private String operatorName;

    private String remark;

    private LocalDateTime createTime;
}