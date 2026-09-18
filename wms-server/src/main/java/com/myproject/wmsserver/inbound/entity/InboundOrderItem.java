package com.myproject.wmsserver.inbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入库单明细。
 *
 * 注意 sku_code / sku_name 是【快照】：建单时从 product 表复制一份存下来。
 * 为什么不用外键 join 查？因为商品改名后，历史单据上显示的必须是当时的名字。
 */
@Data
@TableName("inbound_order_item")
public class InboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    /** 商品编码快照 */
    private String skuCode;

    /** 商品名称快照 */
    private String skuName;

    private Integer expectQty;

    private Integer receivedQty;

    /** 已上架数量，第 ③ 步（完成上架）才回写 */
    private Integer putawayQty;

    /** 收货时登记，无批次用空串 */
    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}