package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 移库单明细。 */
@Data
public class TransferOrderItemDTO {

    @NotNull(message = "商品不能为空")
    private Long skuId;

    @Size(max = 64, message = "批次号最长 64 位")
    private String batchNo;

    @NotNull(message = "移库数量不能为空")
    @Min(value = 1, message = "移库数量必须大于 0")
    private Integer qty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}