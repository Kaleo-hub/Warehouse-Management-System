package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

/** 移库单明细出参。 */
@Data
public class TransferOrderItemVO {

    private Long id;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Integer qty;

    private String remark;
}