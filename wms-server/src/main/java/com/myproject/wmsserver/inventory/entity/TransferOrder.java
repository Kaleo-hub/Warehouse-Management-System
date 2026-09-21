package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 移库单表头。一张单只允许在同一仓库的两个库位之间移动。 */
@Data
@TableName("transfer_order")
public class TransferOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long warehouseId;

    private Long fromLocationId;

    private String fromLocationCode;

    private Long toLocationId;

    private String toLocationCode;

    private String status;

    private Integer totalQty;

    private String remark;

    private LocalDateTime transferTime;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}