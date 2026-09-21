package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

/**
 * 盘点结果。
 *
 * 盘盈 / 盘亏 / 账实相符三种结果都在这里体现，前端不用自己算差异
 * （差异这个数直接决定库存，不给前端算）。
 */
@Data
public class StocktakingResultVO {

    private Long inventoryId;

    private Long locationId;

    private Long skuId;

    private String batchNo;

    /** 盘点前的系统数量 */
    private Integer systemQty;

    /** 实盘数量（前端传进来的） */
    private Integer actualQty;

    /** 差异 = actualQty - systemQty。正数盘盈，负数盘亏，0 账实相符 */
    private Integer diffQty;

    /** STOCKTAKING_GAIN / STOCKTAKING_LOSS；账实相符时为 null（没写流水） */
    private String txType;

    /** 是否真的产生了库存变动。false 表示账实相符，没有写流水 */
    private boolean changed;
}