package com.myproject.wmsserver.inbound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单出参。
 * 列表查询不填 items（保持 null），只有详情接口才带明细，
 * 避免分页时把明细一起查出来（N+1 和返回体积都受不了）。
 */
@Data
public class InboundOrderVO {

    private Long id;

    private String orderNo;

    private Long warehouseId;

    /** 冗余字段：列表要显示仓库名，不能让前端拿 id 去反查 */
    private String warehouseName;

    /** 供应商 id（历史单据可能为空） */
    private Long supplierId;

    private String supplierName;

    private LocalDateTime expectArriveTime;

    private String status;

    private Integer totalExpectQty;

    private Integer totalReceivedQty;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 仅详情接口返回 */
    private List<InboundOrderItemVO> items;
}