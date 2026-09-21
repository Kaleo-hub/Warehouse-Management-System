package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 盘点单单行录入。 */
@Data
public class StocktakingCountLineDTO {

    @NotNull(message = "盘点明细不能为空")
    private Long id;

    @NotNull(message = "实盘数量不能为空")
    @Min(value = 0, message = "实盘数量不能为负数")
    private Integer actualQty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}