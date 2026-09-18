package com.myproject.wmsserver.inventory.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库存分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryQuery extends PageQuery {

    private Long warehouseId;

    private Long skuId;

    private Long locationId;

    /** 批次号精确匹配 */
    private String batchNo;
}