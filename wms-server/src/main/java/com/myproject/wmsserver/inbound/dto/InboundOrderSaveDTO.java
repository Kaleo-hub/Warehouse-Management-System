package com.myproject.wmsserver.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 创建入库单请求体 */
@Data
public class InboundOrderSaveDTO {

    @NotNull(message = "入库仓库不能为空")
    private Long warehouseId;

    @Size(max = 128, message = "供应商名称最长 128 位")
    private String supplierName;

    private LocalDateTime expectArriveTime;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;

    /** @Valid 必须加，否则嵌套的 items 里的校验注解不会生效 —— 这是最常见的漏校验原因 */
    @Valid
    @NotEmpty(message = "入库明细不能为空")
    private List<InboundItemDTO> items;
}