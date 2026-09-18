package com.myproject.wmsserver.basedata.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库区分页查询参数：在通用参数上增加所属仓库过滤 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ZoneQuery extends PageQuery {

    /** 按所属仓库过滤，null 表示不过滤 */
    private Long warehouseId;
}