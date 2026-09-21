package com.myproject.wmsserver.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 创建出库单请求体 */
@Data
public class OutboundOrderSaveDTO {

    @NotNull(message = "出库仓库不能为空")
    private Long warehouseId;

    /** 客户 id。填了以主数据为准，不填则退回自由文本 */
    private Long customerId;

    @Size(max = 128, message = "客户名称最长 128 位")
    private String customerName;

    @Size(max = 255, message = "收货地址最长 255 位")
    private String receiveAddress;

    private LocalDateTime expectShipTime;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;

    /** @Valid 必须加，否则嵌套明细里的校验注解不会生效 */
    @Valid
    @NotEmpty(message = "出库明细不能为空")
    private List<OutboundItemDTO> items;
}