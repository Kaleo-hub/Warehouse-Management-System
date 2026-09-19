package com.myproject.wmsserver.outbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库库存分配明细 —— 这条记录就是「哪个库位的哪些货被哪张出库单占住了」。
 *
 * 一条出库单明细可能对应多条分配记录：
 *   需要 100 件，A 库位批次早的有 60、B 库位批次晚的有 80
 *   → 分配成两条：A 库位 60 + B 库位 40
 * 这就是 FIFO 按批次拆分的结果。
 */
@Data
@TableName("outbound_allocate")
public class OutboundAllocate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long orderItemId;

    /** 锁定的库存记录 id */
    private Long inventoryId;

    private Long warehouseId;

    private Long locationId;

    private Long skuId;

    private String batchNo;

    /** 本条分配的数量 */
    private Integer allocatedQty;

    /** 本条已拣货数量 */
    private Integer pickedQty;

    /** AllocateStatus 枚举名：LOCKED / PICKED / RELEASED */
    private String status;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}