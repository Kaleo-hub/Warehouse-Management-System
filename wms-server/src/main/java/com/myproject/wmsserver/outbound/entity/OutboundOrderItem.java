package com.myproject.wmsserver.outbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库单明细。
 * skuCode / skuName 同样是建单时的快照，和入库单一个道理 ——
 * 单据是历史事实，商品改名后不能跟着变。
 */
@Data
@TableName("outbound_order_item")
public class OutboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    /** 需求数量 */
    private Integer qty;

    /** 已分配数量 */
    private Integer allocatedQty;

    /** 已拣货数量 */
    private Integer pickedQty;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}