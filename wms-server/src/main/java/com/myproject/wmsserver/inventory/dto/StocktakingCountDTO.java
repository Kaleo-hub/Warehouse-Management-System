package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 批量保存盘点结果，允许分多次录入。 */
@Data
public class StocktakingCountDTO {

    @Valid
    @NotEmpty(message = "盘点明细不能为空")
    private List<StocktakingCountLineDTO> items;
}