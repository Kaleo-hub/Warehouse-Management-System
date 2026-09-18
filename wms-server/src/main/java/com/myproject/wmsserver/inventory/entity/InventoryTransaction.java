package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水。只增不改不删（BR-008）。
 *
 * 注意这张表【没有】deleted 字段，所以全局逻辑删除配置对它不生效，这正是我们想要的：
 * 流水一旦写入就永远留着，对账和审计都靠它。
 */
@Data
@TableName("inventory_transaction")
public class InventoryTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号，全局唯一 */
    private String txNo;

    private Long warehouseId;

    private Long locationId;

    private Long skuId;

    private String batchNo;

    /** InventoryTxType 的枚举名 */
    private String txType;

    /** 本次在库数量变动，可正可负 */
    private Integer qtyChange;

    /** 本次锁定数量变动 */
    private Integer lockedChange;

    /** 本次冻结数量变动 */
    private Integer frozenChange;

    /** 变动前 qty —— 对账时看相邻流水是否连续 */
    private Integer qtyBefore;

    /** 变动后 qty */
    private Integer qtyAfter;

    /** INBOUND_ORDER / OUTBOUND_ORDER / TASK / MANUAL / STOCKTAKING */
    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long operatorId;

    private String operatorName;

    private String remark;

    private LocalDateTime createTime;
}