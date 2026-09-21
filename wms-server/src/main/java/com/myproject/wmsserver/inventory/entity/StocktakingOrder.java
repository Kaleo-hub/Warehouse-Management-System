package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 盘点单表头。 */
@Data
@TableName("stocktaking_order")
public class StocktakingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long warehouseId;

    private Long locationId;

    private String locationCode;

    private String locationName;

    private String status;

    private Integer totalItems;

    private Integer countedItems;

    private Integer diffItems;

    private Integer gainQty;

    private Integer lossQty;

    private String remark;

    private LocalDateTime postTime;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}