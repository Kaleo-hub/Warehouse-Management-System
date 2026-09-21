package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 盘点请求体。
 *
 * 【关键设计：前端传「实盘数量」，不传差异】
 * 差异由后端拿系统数量现算：diff = actualQty - qty。
 * 如果让前端传差异，前端就得先知道系统数量（多一次往返），
 * 而且给了前端算错 / 造假的机会 —— 盘点的全部意义就是「数了多少就是多少」。
 *
 * 【为什么 actualQty 允许 0】
 * 盘点发现这个库位这批货一件不剩，是合法结果（哪怕最后盘亏会被拦）。
 * 真正要拦的是「盘亏后数量小于 locked + frozen」：那说明还有货被订单占用或冻结着，
 * 实物不可能一件都没有 —— 这个校验在 applyChange 里做（可用数量不能为负）。
 */
@Data
public class StocktakingDTO {

    @NotNull(message = "库位不能为空")
    private Long locationId;

    @NotNull(message = "商品不能为空")
    private Long skuId;

    /** 批次号，无批次不传 */
    @Size(max = 64, message = "批次号最长 64 位")
    private String batchNo;

    /** 实盘数量，>= 0 */
    @NotNull(message = "实盘数量不能为空")
    @Min(value = 0, message = "实盘数量不能为负数")
    private Integer actualQty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}