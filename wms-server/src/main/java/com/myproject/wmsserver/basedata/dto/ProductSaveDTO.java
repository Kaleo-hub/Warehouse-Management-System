package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商品新增 / 修改请求体（接口文档 6.3） */
@Data
public class ProductSaveDTO {

    /** SKU 编码，全局唯一，≤64 */
    @NotBlank(message = "SKU 编码不能为空")
    @Size(max = 64, message = "SKU 编码最长 64 位")
    private String skuCode;

    /** 商品名称，≤128 */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称最长 128 位")
    private String name;

    /** 规格，如 500ml/瓶，≤64 */
    @Size(max = 64, message = "规格最长 64 位")
    private String spec;

    /** 计量单位，≤16 */
    @Size(max = 16, message = "单位最长 16 位")
    private String unit;

    /** 条形码，≤64，可为空 */
    @Size(max = 64, message = "条形码最长 64 位")
    private String barcode;

    /** 重量（g） */
    @Min(value = 0, message = "重量不能为负数")
    private Integer weightG;

    /** 保质期（天），0 表示不管效期 */
    @NotNull(message = "保质期不能为空")
    @Min(value = 0, message = "保质期不能为负数")
    private Integer shelfLifeDays;

    /** ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    private String status;
}