package com.myproject.wmsserver.inbound.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 入库单分页查询条件。
 * page / size / keyword / status 继承自 PageQuery，这里补入库特有的过滤字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InboundOrderQuery extends PageQuery {

    private Long warehouseId;

    /** 创建时间范围（前端传 yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}