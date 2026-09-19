package com.myproject.wmsserver.outbound.vo;

import lombok.Data;

/**
 * 分配明细出参。
 * 前端详情页要展示「这单货从哪些库位的哪些批次出」，
 * 所以 locationCode / batchNo / skuCode / skuName 都要冗余返回。
 */
@Data
public class OutboundAllocateVO {

    private Long id;

    private Long orderItemId;

    private Long inventoryId;

    private Long locationId;

    private String locationCode;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Integer allocatedQty;

    private Integer pickedQty;

    /** LOCKED / PICKED / RELEASED */
    private String status;
}