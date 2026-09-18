package com.myproject.wmsserver.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 库区响应体，字段见接口文档 6.2 */
@Data
public class ZoneVO {

    private Long id;

    private Long warehouseId;

    /** 冗余返回仓库名称，前端不用靠下拉缓存反查 */
    private String warehouseName;

    private String code;

    private String name;

    /** STORAGE / PICKING / RECEIVING / SHIPPING / SORTING */
    private String zoneType;

    private Integer sortNo;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}