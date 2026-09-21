package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 盘点单出参。 */
@Data
public class StocktakingOrderVO {

    private Long id;

    private String orderNo;

    private Long warehouseId;

    private String warehouseName;

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<StocktakingOrderItemVO> items;
}