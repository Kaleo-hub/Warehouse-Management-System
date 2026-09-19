package com.myproject.wmsserver.outbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 出库单表头。表结构见 sql/V1.0.0__init_schema.sql */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出库单号，如 OUT20260919000001 */
    private String orderNo;

    private Long warehouseId;

    private String customerName;

    private String receiveAddress;

    private LocalDateTime expectShipTime;

    /** OutboundStatus 枚举名 */
    private String status;

    /** 需求总数量 */
    private Integer totalQty;

    /** 已分配总数量 */
    private Integer allocatedQty;

    /** 已拣货总数量 */
    private Integer pickedQty;

    /** 分配完成时间，用于后续做超时自动释放 */
    private LocalDateTime allocateTime;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}