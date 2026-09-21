package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 移库单明细。表头固定移出/移入库位，明细描述每个 SKU + 批次移动多少。 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Integer qty;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}