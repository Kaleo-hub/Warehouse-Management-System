package com.myproject.wmsserver.basedata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品（SKU）。表结构见 db/migration/V1.0.0__init_schema.sql */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU 编码，全局唯一，≤64 */
    private String skuCode;

    /** 商品名称，≤128 */
    private String name;

    /** 规格，如 500ml/瓶，≤64 */
    private String spec;

    /** 计量单位，≤16，默认「件」 */
    private String unit;

    /** 条形码，≤64，可为空 */
    private String barcode;

    /** 长（mm），可为空 */
    private Integer lengthMm;

    /** 宽（mm），可为空 */
    private Integer widthMm;

    /** 高（mm），可为空 */
    private Integer heightMm;

    /** 重量（g），可为空 */
    private Integer weightG;

    /** 保质期（天），0 表示不管效期 */
    private Integer shelfLifeDays;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注，≤255 */
    private String remark;

    /** 逻辑删除：0 未删除，1 已删除 */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}