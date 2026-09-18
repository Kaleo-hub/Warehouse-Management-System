package com.myproject.wmsserver.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 完成作业请求体 */
@Data
public class StockTaskCompleteDTO {

    /**
     * 实际上架数量。
     * 正常情况下等于 planQty；少了说明作业过程中有破损/差异，
     * 库存按实际数量增加 —— 账实一致优先于计划数量。
     */
    @NotNull(message = "实际上架数量不能为空")
    @Min(value = 0, message = "实际上架数量不能为负")
    private Integer actualQty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}