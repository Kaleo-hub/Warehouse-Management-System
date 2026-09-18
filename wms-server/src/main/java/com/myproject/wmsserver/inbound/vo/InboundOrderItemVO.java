package com.myproject.wmsserver.inbound.vo;

import lombok.Data;

import java.time.LocalDate;

/** 入库单明细出参 */
@Data
public class InboundOrderItemVO {

    /** 明细 id。收货时前端传的就是它 */
    private Long id;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private Integer expectQty;

    private Integer receivedQty;

    private Integer putawayQty;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private String remark;
}