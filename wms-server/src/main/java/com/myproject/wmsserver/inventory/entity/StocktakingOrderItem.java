package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 盘点单明细。每行对应建单时的一条库存余额快照。 */
@Data
@TableName("stocktaking_order_item")
public class StocktakingOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

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

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}