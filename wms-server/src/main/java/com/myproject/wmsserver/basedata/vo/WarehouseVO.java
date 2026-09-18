package com.myproject.wmsserver.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 仓库响应体，字段见接口文档 6.2 */
@Data
public class WarehouseVO {

    private Long id;

    private String code;

    private String name;

    private String address;

    /** ENABLED / DISABLED */
    private String status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}