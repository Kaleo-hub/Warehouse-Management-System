package com.myproject.wmsserver.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存。表结构见 sql/V1.0.0__init_schema.sql
 *
 * 【重要】不要用这个实体直接做业务查询返回给前端，
 * 查询请用 InventoryVO（带 warehouseName / locationCode / skuCode 这些冗余字段）。
 */
@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long locationId;

    private Long skuId;

    /** 批次号。没批次时用空串，不要用 null —— 唯一键 uk_loc_sku_batch 含这一列，null 会让唯一约束失效 */
    private String batchNo;

    /** 在库实物数量 */
    private Integer qty;

    /** 已分配未发货数量 */
    private Integer lockedQty;

    /** 冻结数量 */
    private Integer frozenQty;

    /**
     * 可用数量 = qty - locked_qty - frozen_qty，是 MySQL 的 STORED 生成列。
     * 只能读，不能写 —— 写它数据库会直接报错。
     * 这里用 FieldStrategy.NEVER 明确告诉 MyBatis-Plus：insert / update 都不要带上这一列。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableQty;

    /** 乐观锁版本号，每次变动 +1 */
    private Integer version;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    /** 首次入库时间，只写一次，出库 FIFO 靠它排序 */
    private LocalDateTime inboundTime;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}