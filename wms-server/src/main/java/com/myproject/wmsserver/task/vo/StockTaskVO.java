package com.myproject.wmsserver.task.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务出参。
 *
 * warehouseName / skuCode / skuName / fromLocationCode / toLocationCode / assigneeName
 * 这些冗余字段是必须的 —— 任务列表要直接显示名称，
 * 不能只给一堆 id 让前端再反查。
 */
@Data
public class StockTaskVO {

    private Long id;

    private String taskNo;

    private String taskType;

    private String status;

    private Long warehouseId;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String batchNo;

    private Long fromLocationId;

    private String fromLocationCode;

    private Long toLocationId;

    private String toLocationCode;

    private Integer planQty;

    private Integer actualQty;

    private Long assigneeId;

    private String assigneeName;

    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Integer priority;

    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private String remark;
}