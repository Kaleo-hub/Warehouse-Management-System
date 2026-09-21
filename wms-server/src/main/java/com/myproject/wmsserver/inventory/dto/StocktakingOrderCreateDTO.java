package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建盘点单：选定一个库位，后端把该库位当前库存行全部快照成明细。 */
@Data
public class StocktakingOrderCreateDTO {

    @NotNull(message = "盘点库位不能为空")
    private Long locationId;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}