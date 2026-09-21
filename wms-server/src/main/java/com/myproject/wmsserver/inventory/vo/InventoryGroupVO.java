package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

/**
 * 库存分组合举行。
 *
 * 三个维度共用这一个出参（按仓库 / 按库区 / 按 SKU），字段含义随维度变化：
 *   WAREHOUSE → groupId=仓库id, groupCode=仓库编码, groupName=仓库名
 *   ZONE      → groupId=库区id, groupCode=库区编码, groupName=库区名, parentName=所属仓库
 *   SKU       → groupId=商品id, groupCode=SKU编码, groupName=商品名
 *
 * 数量字段用 Long：聚合结果可能超过 int 范围（虽然学习项目不会）。
 */
@Data
public class InventoryGroupVO {

    private Long groupId;

    /** 编码：仓库编码 / 库区编码 / SKU 编码 */
    private String groupCode;

    /** 名称 */
    private String groupName;

    /** 仅 ZONE 维度有值：STORAGE / PICKING / RECEIVING / SHIPPING / SORTING */
    private String groupType;

    /** 上级名称（库区维度放所属仓库名） */
    private String parentName;

    private Long qty;

    private Long lockedQty;

    private Long frozenQty;

    private Long availableQty;

    /** 该组里有几个 SKU（SKU 维度恒为 1） */
    private Integer skuCount;

    /** 该组占了几个库位 */
    private Integer locationCount;
}