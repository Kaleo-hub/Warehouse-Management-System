package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建移库单。 */
@Data
public class TransferOrderCreateDTO {

    @NotNull(message = "移出库位不能为空")
    private Long fromLocationId;

    @NotNull(message = "移入库位不能为空")
    private Long toLocationId;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;

    @Valid
    @NotEmpty(message = "移库明细不能为空")
    private List<TransferOrderItemDTO> items;
}