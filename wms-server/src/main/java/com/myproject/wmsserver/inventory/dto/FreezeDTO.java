package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 冻结 / 解冻请求体。
 *
 * 【为什么只有一个 qty，没有 skuId / locationId】
 * 冻的是「某一条库存行」的一部分，是哪一行由路径上的 inventoryId 决定。
 * 一条库存行 = 库位 + SKU + 批次，所以 id 本身就唯一确定了这一行，不用前端再传一遍。
 *
 * 【冻结和出库的区别，面试常问】
 *   冻结：qty 不变，frozen_qty +n，货还在库里，只是不能分配、不能移库（BR-005）
 *   出库：qty -n，货真的走了
 * 所以冻结只是「把可用数量扣掉」，是一条完全可逆的操作（解冻即可）。
 */
@Data
public class FreezeDTO {

    /** 本次冻结 / 解冻的数量，必须大于 0 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer qty;
}