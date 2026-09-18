package com.myproject.wmsserver.task.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 作业任务分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockTaskQuery extends PageQuery {

    /** PUTAWAY / PICK / TRANSFER / COUNT */
    private String taskType;

    private Long warehouseId;

    private Long assigneeId;

    /** 来源单号，例如按入库单号查它派生的任务 */
    private String sourceNo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}