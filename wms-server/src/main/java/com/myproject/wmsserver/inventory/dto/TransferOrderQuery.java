package com.myproject.wmsserver.inventory.dto;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 移库单分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferOrderQuery extends PageQuery {

    private Long warehouseId;

    private Long fromLocationId;

    private Long toLocationId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;
}