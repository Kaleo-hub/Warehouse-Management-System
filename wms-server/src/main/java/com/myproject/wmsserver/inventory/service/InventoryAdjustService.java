package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.inventory.dto.FreezeDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingDTO;
import com.myproject.wmsserver.inventory.dto.TransferDTO;
import com.myproject.wmsserver.inventory.vo.ReconcileVO;
import com.myproject.wmsserver.inventory.vo.StocktakingResultVO;

/**
 * 库存调整类业务：冻结 / 解冻 / 移库 / 盘点 / 对账。
 *
 * 【为什么不直接塞进 InventoryService】
 * 分成两层：业务动作（本接口）→ 写入内核（InventoryService.applyChange）→ SQL。
 *
 *   InventoryService：只有 applyChange 一个方法，负责「锁行 + 算 + 校验 + CAS + 写流水」，
 *                     它不知道什么是移库、什么是盘点。
 *   本接口：负责业务规则（目标库位启用了吗？冻结量够解冻吗？哪些算盘盈盘亏？），
 *           然后把结果翻译成一条或两条 applyChange。
 *
 * 好处是以后加「质检冻结」「报废出库」这类新动作，只需要往这一层加方法，
 * 写入内核一行都不用动 —— 而且它们天然继承了锁、CAS、流水这三重保障。
 */
public interface InventoryAdjustService {

    /**
     * 冻结：frozen_qty +qty。
     * 货还在库里（qty 不变），只是可用数量变少；冻结的库存不可分配、不可移库（BR-005）。
     */
    void freeze(Long inventoryId, FreezeDTO dto);

    /** 解冻：frozen_qty -qty，可用数量恢复。 */
    void unfreeze(Long inventoryId, FreezeDTO dto);

    /**
     * 移库：把某库位某批次的一部分或全部挪到另一个库位。
     * 一个事务里转出 -qty、转入 +qty，两步必须同生共死。
     */
    void transfer(TransferDTO dto);

    /**
     * 盘点：传实盘数量，后端算差异。
     * 盘盈 +diff、盘亏 -diff，各写一条流水；账实相同时不写流水。
     */
    StocktakingResultVO stocktake(StocktakingDTO dto);

    /**
     * 对账：按流水重算库存余额，和 inventory 表上的 qty 比对（BR-009）。
     *
     * @param autoFix true 时按流水重算并修正余额（修正的是派生值，不写库存流水，
     *                理由见 InventoryAdjustServiceImpl#fixOne）；
     *                false 只检查不修改，用于「先看看账平不平」
     */
    ReconcileVO reconcile(boolean autoFix);
}