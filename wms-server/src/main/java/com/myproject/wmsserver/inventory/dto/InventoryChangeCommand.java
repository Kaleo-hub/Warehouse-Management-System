package com.myproject.wmsserver.inventory.dto;

import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import lombok.Builder;
import lombok.Data;

/**
 * applyChange 的入参。
 *
 * 为什么用 @Builder 而不是一堆参数？
 * 因为参数有 12 个，用 Builder 调用处一眼能看出每个值是什么：
 *
 *   InventoryChangeCommand.builder()
 *       .warehouseId(1L).locationId(1L).skuId(3L)
 *       .batchNo("B20260901")
 *       .qtyChange(200)
 *       .txType(InventoryTxType.PUTAWAY_IN)
 *       .sourceType("TASK").sourceNo(task.getTaskNo())
 *       .build();
 */
@Data
@Builder
public class InventoryChangeCommand {

    private Long warehouseId;

    private Long locationId;

    private Long skuId;

    /** 无批次传空串或 null，内部会统一转成空串 */
    private String batchNo;

    /** 在库数量变动，可正可负。上架 +，发货 - */
    private int qtyChange;

    /** 锁定数量变动。出库分配 +，释放 - */
    private int lockedChange;

    /** 冻结数量变动 */
    private int frozenChange;

    private InventoryTxType txType;

    /** INBOUND_ORDER / OUTBOUND_ORDER / TASK / MANUAL / STOCKTAKING */
    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long operatorId;

    private String operatorName;

    private String remark;

    /**
     * 库存行不存在时，是否自动创建一条 0 库存的记录。
     *
     * true  —— 入库类变动用。第一次上架时这个「库位+SKU+批次」还没库存，
     *          需要先建行才能加锁（锁不存在的行是无效的）。
     * false —— 出库类变动必须用。没库存就是没库存，
     *          不能因为「分配」这个动作凭空建出一条 0 库存记录来。
     */
    @Builder.Default
    private boolean createIfAbsent = true;
}