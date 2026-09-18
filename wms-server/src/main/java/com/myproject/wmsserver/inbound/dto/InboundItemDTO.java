package com.myproject.wmsserver.inbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建入库单时的一行明细。批号和日期在收货时才登记，这里不传。 */
@Data
public class InboundItemDTO {

    @NotNull(message = "商品不能为空")
    private Long skuId;

    @NotNull(message = "预计数量不能为空")
    @Min(value = 1, message = "预计数量必须大于 0")
    private Integer expectQty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}