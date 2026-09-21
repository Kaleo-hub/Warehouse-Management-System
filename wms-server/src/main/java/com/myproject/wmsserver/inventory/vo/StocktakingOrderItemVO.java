package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

/** 盘点单明细出参。 */
@Data
public class StocktakingOrderItemVO {

    private Long id;

    private Long inventoryId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Integer systemQty;

    private Integer lockedQty;

    private Integer frozenQty;

    private Integer actualQty;

    private Integer diffQty;

    private String remark;
}