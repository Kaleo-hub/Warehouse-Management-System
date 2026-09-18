package com.myproject.wmsserver.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 库位响应体，字段见接口文档 6.2 */
@Data
public class LocationVO {

    private Long id;

    private Long warehouseId;

    /** 冗余返回仓库名称 */
    private String warehouseName;

    private Long zoneId;

    /** 冗余返回库区名称 */
    private String zoneName;

    private String code;

    private String name;

    /** RACK / FLOOR / TEMP */
    private String locationType;

    /** 容量上限，0 表示不限 */
    private Integer capacity;

    /** 已用容量，由库存变动维护 */
    private Integer usedCapacity;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}