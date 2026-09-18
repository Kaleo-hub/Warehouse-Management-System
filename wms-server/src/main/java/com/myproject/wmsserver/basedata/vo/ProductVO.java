package com.myproject.wmsserver.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 商品响应体，字段见接口文档 6.2 */
@Data
public class ProductVO {

    private Long id;

    private String skuCode;

    private String name;

    /** 规格，如 500ml/瓶 */
    private String spec;

    /** 计量单位 */
    private String unit;

    private String barcode;

    /** 重量（g） */
    private Integer weightG;

    /** 保质期（天），0 表示不管效期 */
    private Integer shelfLifeDays;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}