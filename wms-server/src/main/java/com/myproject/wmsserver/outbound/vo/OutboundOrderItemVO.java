package com.myproject.wmsserver.outbound.vo;

import lombok.Data;

/** 出库单明细出参 */
@Data
public class OutboundOrderItemVO {

    private Long id;

    private Long skuId;

    private String skuCode;

    private String skuName;

    /** 需求数量 */
    private Integer qty;

    private Integer allocatedQty;

    private Integer pickedQty;

    private String remark;
}