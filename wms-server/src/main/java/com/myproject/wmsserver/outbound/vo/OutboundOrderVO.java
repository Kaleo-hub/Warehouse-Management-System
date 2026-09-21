package com.myproject.wmsserver.outbound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单出参。
 * 列表不填 items / allocates，只有详情才带，避免分页时把明细一起查出来。
 */
@Data
public class OutboundOrderVO {

    private Long id;

    private String orderNo;

    private Long warehouseId;

    /** 冗余字段，列表直接显示仓库名 */
    private String warehouseName;

    /** 客户 id（历史单据可能为空） */
    private Long customerId;

    private String customerName;

    private String receiveAddress;

    private LocalDateTime expectShipTime;

    private String status;

    private Integer totalQty;

    private Integer allocatedQty;

    private Integer pickedQty;

    private LocalDateTime allocateTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 仅详情接口返回 */
    private List<OutboundOrderItemVO> items;

    /** 仅详情接口返回：这一单锁定了哪些库位的哪些批次 */
    private List<OutboundAllocateVO> allocates;
}