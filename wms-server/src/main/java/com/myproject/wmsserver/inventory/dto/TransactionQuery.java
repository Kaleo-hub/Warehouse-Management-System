package com.myproject.wmsserver.inventory.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 库存流水分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionQuery extends PageQuery {

    /** PUTAWAY_IN / OUTBOUND_OUT / FREEZE ... */
    private String txType;

    private Long skuId;

    private Long locationId;

    /** 来源单号：按入库单号查它产生的所有流水 */
    private String sourceNo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}