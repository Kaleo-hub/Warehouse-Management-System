package com.myproject.wmsserver.outbound.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 出库单分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundOrderQuery extends PageQuery {

    private Long warehouseId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}