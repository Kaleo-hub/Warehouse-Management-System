package com.myproject.wmsserver.basedata.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库位分页查询参数：在通用参数上增加仓库 / 库区过滤 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationQuery extends PageQuery {

    /** 按所属仓库过滤，null 表示不过滤 */
    private Long warehouseId;

    /** 按所属库区过滤，null 表示不过滤 */
    private Long zoneId;
}