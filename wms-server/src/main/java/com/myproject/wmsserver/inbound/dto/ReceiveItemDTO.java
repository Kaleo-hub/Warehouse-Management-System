package com.myproject.wmsserver.inbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 收货登记时的一行 */
@Data
public class ReceiveItemDTO {

    /** 入库单明细 id（不是 skuId） */
    @NotNull(message = "明细 id 不能为空")
    private Long itemId;

    /** 本次实收数量，必须大于 0 */
    @NotNull(message = "实收数量不能为空")
    @Min(value = 1, message = "实收数量必须大于 0")
    private Integer receivedQty;

    @Size(max = 64, message = "批次号最长 64 位")
    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;
}