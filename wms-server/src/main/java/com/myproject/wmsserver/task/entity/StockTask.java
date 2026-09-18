package com.myproject.wmsserver.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 作业任务。表结构见 sql/V1.0.0__init_schema.sql */
@Data
@TableName("stock_task")
public class StockTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务号，如 TASK20260918000001 */
    private String taskNo;

    /** TaskType 枚举名 */
    private String taskType;

    private Long warehouseId;

    /** INBOUND_ORDER / OUTBOUND_ORDER / MANUAL */
    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    /** 来源单据的明细 id —— 上架完成后要靠它回写 putaway_qty */
    private Long sourceItemId;

    /** TaskStatus 枚举名 */
    private String status;

    private Integer priority;

    private Long assigneeId;

    private String assigneeName;

    /** 源库位（上架时是收货暂存位） */
    private Long fromLocationId;

    /** 目标库位（上架时是推荐出来的存储位） */
    private Long toLocationId;

    private Long skuId;

    private String batchNo;

    private Integer planQty;

    private Integer actualQty;

    private LocalDateTime deadline;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Integer retryCount;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}