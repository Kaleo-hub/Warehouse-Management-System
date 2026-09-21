package com.myproject.wmsserver.inventory.dto;

import lombok.Data;

/**
 * 汇总 / 预警查询条件。
 *
 * 汇总和预警都是「看板类」查询，参数很少，合并成一个 DTO 减少类数量。
 */
@Data
public class SummaryQuery {

    /** 汇总维度：WAREHOUSE / ZONE / SKU，默认按仓库 */
    private String dimension = "WAREHOUSE";

    /** 临期预警阈值（天），默认 30 天 */
    private Integer expireDays = 30;

    /** 可选：只看某个仓库 */
    private Long warehouseId;
}