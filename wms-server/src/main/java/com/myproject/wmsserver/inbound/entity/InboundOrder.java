package com.myproject.wmsserver.inbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 入库单表头。表结构见 sql/V1.0.0__init_schema.sql */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号，如 IN20260918000001，全局唯一 */
    private String orderNo;

    private Long warehouseId;

    /** 供应商名称，快照 */
    private String supplierName;

    private LocalDateTime expectArriveTime;

    /** InboundStatus 的枚举名 */
    private String status;

    /** 预计总数量 = 所有明细 expect_qty 之和 */
    private Integer totalExpectQty;

    /** 实收总数量 = 所有明细 received_qty 之和 */
    private Integer totalReceivedQty;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}