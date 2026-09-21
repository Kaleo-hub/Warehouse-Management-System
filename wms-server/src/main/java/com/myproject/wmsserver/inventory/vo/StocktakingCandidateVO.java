package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

/** 建盘点单时从库存行生成的候选快照。 */
@Data
public class StocktakingCandidateVO {

    private Long inventoryId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Integer systemQty;

    private Integer lockedQty;

    private Integer frozenQty;
}