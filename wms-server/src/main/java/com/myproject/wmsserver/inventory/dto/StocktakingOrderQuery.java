package com.myproject.wmsserver.inventory.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 盘点单分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StocktakingOrderQuery extends PageQuery {

    private Long warehouseId;

    private Long locationId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}