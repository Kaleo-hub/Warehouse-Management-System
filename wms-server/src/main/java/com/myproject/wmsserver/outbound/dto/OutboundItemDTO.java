package com.myproject.wmsserver.outbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建出库单时的一行明细 */
@Data
public class OutboundItemDTO {

    @NotNull(message = "商品不能为空")
    private Long skuId;

    @NotNull(message = "出库数量不能为空")
    @Min(value = 1, message = "出库数量必须大于 0")
    private Integer qty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}