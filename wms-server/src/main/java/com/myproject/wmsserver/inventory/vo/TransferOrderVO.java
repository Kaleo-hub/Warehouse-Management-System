package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 移库单出参。 */
@Data
public class TransferOrderVO {

    private Long id;

    private String orderNo;

    private Long warehouseId;

    private String warehouseName;

    private Long fromLocationId;

    private String fromLocationCode;

    private Long toLocationId;

    private String toLocationCode;

    private String status;

    private Integer totalQty;

    private String remark;

    private LocalDateTime transferTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<TransferOrderItemVO> items;
}