package com.myproject.wmsserver.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 移库请求体：把某个库位上的一批货，整批或部分挪到另一个库位。
 *
 * 【为什么不用传 warehouseId】
 * 「库位 + SKU + 批次」已经唯一确定一条库存行，仓库可以从这条库存行反查出来。
 * 让前端传仓库，只会多一个「传错仓库」的出错口子 —— 前端只需要选源库位、SKU、批次、目标库位。
 * 后端会校验目标库位和源库位在同一个仓库（跨仓库那叫调拨，是另一套单据，不是移库）。
 *
 * 【为什么要有 remark】
 * 移库是最常见的人为判断操作（拣货位补货、货放错位置要挪），
 * 谁、什么时候、为什么挪的，出了事要能查，所以流水上留一句话。
 */
@Data
public class TransferDTO {

    /** 移出库位 */
    @NotNull(message = "移出库位不能为空")
    private Long fromLocationId;

    /** 移入库位 */
    @NotNull(message = "移入库位不能为空")
    private Long toLocationId;

    @NotNull(message = "商品不能为空")
    private Long skuId;

    /** 批次号，无批次不传 */
    @Size(max = 64, message = "批次号最长 64 位")
    private String batchNo;

    /** 移库数量，必须大于 0 */
    @NotNull(message = "移库数量不能为空")
    @Min(value = 1, message = "移库数量必须大于 0")
    private Integer qty;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}