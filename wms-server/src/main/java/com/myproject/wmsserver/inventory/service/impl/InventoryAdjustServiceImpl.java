package com.myproject.wmsserver.inventory.service.impl;

import com.myproject.wmsserver.basedata.entity.Location;
import com.myproject.wmsserver.basedata.mapper.LocationMapper;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.inventory.dto.FreezeDTO;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.dto.StocktakingDTO;
import com.myproject.wmsserver.inventory.dto.TransferDTO;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.service.InventoryAdjustService;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.inventory.vo.ReconcileVO;
import com.myproject.wmsserver.inventory.vo.StocktakingResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 冻结 / 移库 / 盘点 / 对账的实现。
 *
 * ========== 这一层的三条铁律 ==========
 *
 * 1) 所有【库存变动】都必须拼一条 InventoryChangeCommand 交给 applyChange，这里绝不自己 UPDATE。
 *    这里注入的 InventoryMapper 基本只用于读（按 id 取一行、按业务键取一行、跑对账查询）。
 *
 *    唯一的例外是对账修正 repairBalance：那不是库存变动，是重建派生数据
 *    （余额本来就是流水 SUM 出来的），没有货进出仓库，所以它不该、也不能写流水。
 *    硬要它走 applyChange 的话，修正流水会改变流水 SUM，导致下一次对账又对不平、永远收敛不了 ——
 *    这一点是用 InventoryAdjustServiceTest 里的对账用例跑出来的，不是推理出来的。
 *
 * 2) 出库类方向一律 createIfAbsent = false。
 *    冻一条不存在的库存、盘一个没货的库位，应该报「库存记录不存在」，
 *    而不是先建一条 0 库存的垃圾行再失败。
 *    唯一例外是移库的【转入】侧：目标库位可能还没有这个批次，必须允许建行。
 *
 * 3) 需要「两步一起成功」的（移库）用一个事务包起来。
 *    applyChange 是别的 Bean，事务传播是 REQUIRED —— 外层开了事务，两次调用就并入同一个事务，
 *    转入失败时转出也会一起回滚，不会出现「货从 A 移走了但没到 B」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustServiceImpl implements InventoryAdjustService {

    private static final String LOCATION_STATUS_ENABLED = "ENABLED";

    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final LocationMapper locationMapper;
    private final LocationService locationService;

    /* ==================== 冻结 / 解冻 ==================== */

    @Override
    public void freeze(Long inventoryId, FreezeDTO dto) {
        Inventory inventory = requireInventory(inventoryId);
        int qty = requirePositive(dto.getQty());

        // 冻结只加 frozen_qty，qty 一分不动 —— 货还在库里，只是不能用了。
        // 「可用数量够不够冻」不用在这里判：applyChange 的不变量校验
        // （locked + frozen <= qty）会挡住，而且那个校验是在锁住行之后做的，才靠谱。
        inventoryService.applyChange(baseCommand(inventory)
                .frozenChange(qty)
                .txType(InventoryTxType.FREEZE)
                .remark("手动冻结")
                .build());
    }

    @Override
    public void unfreeze(Long inventoryId, FreezeDTO dto) {
        Inventory inventory = requireInventory(inventoryId);
        int qty = requirePositive(dto.getQty());

        // 先给个明确的报错：解冻 20 但只冻了 10 的时候，
        // 让 applyChange 去报「可用库存不足」会非常难懂，这里直接说清是解冻量超了。
        if (qty > nz(inventory.getFrozenQty())) {
            log.warn("解冻数量超过冻结数量 inventoryId={} 冻结={} 请求解冻={}",
                    inventoryId, inventory.getFrozenQty(), qty);
            throw new BizException(InventoryErrorCode.FREEZE_NOT_ENOUGH);
        }

        inventoryService.applyChange(baseCommand(inventory)
                .frozenChange(-qty)
                .txType(InventoryTxType.UNFREEZE)
                .remark("手动解冻")
                .build());
    }

    /* ==================== 移库 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(TransferDTO dto) {
        if (dto.getFromLocationId().equals(dto.getToLocationId())) {
            throw new BizException(InventoryErrorCode.TRANSFER_SAME_LOCATION);
        }
        int qty = requirePositive(dto.getQty());
        String batchNo = normalizeBatchNo(dto.getBatchNo());

        // 「库位 + SKU + 批次」三个值唯一确定一条库存行，源库存就从这里查。
        // 注意不能用前端传的 warehouseId —— 那是可以造假的输入，仓库要从库存行本身读出来。
        Inventory source = inventoryMapper.selectByBusinessKey(
                dto.getFromLocationId(), dto.getSkuId(), batchNo);
        if (source == null) {
            log.warn("移库失败：源库存不存在 locationId={} skuId={} batchNo={}",
                    dto.getFromLocationId(), dto.getSkuId(), batchNo);
            throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
        }

        Location target = locationMapper.selectById(dto.getToLocationId());
        if (target == null || !LOCATION_STATUS_ENABLED.equals(target.getStatus())) {
            log.warn("移库失败：目标库位不存在或不可用 toLocationId={}", dto.getToLocationId());
            throw new BizException(InventoryErrorCode.LOCATION_DISABLED);
        }
        // 跨仓库搬货不是「挪个位置」：那中间有运输在途，是调拨单该管的事，不能走移库
        if (!target.getWarehouseId().equals(source.getWarehouseId())) {
            log.warn("移库失败：跨仓库 fromWarehouse={} toWarehouse={}",
                    source.getWarehouseId(), target.getWarehouseId());
            throw new BizException(InventoryErrorCode.TRANSFER_CROSS_WAREHOUSE);
        }
        // 冻结 / 已被订单锁定的货不能移（BR-005）。这里提前拦一次是为了给前端一个干净的报错，
        // 真正的兜底在 applyChange 里 —— 那里的校验是在锁住行之后做的，不会被并发绕过。
        if (nz(source.getAvailableQty()) < qty) {
            log.warn("移库失败：可用数量不足 inventoryId={} 可用={} 需要={}",
                    source.getId(), source.getAvailableQty(), qty);
            throw new BizException(InventoryErrorCode.AVAILABLE_NOT_ENOUGH);
        }

        // ① 转出：源库位 qty -qty。createIfAbsent = false（货本来就在这，不存在就是数据有问题）
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(source.getWarehouseId())
                .locationId(source.getLocationId())
                .skuId(source.getSkuId())
                .batchNo(source.getBatchNo())
                .qtyChange(-qty)
                .txType(InventoryTxType.TRANSFER_OUT)
                .sourceType("MANUAL")
                .remark(dto.getRemark())
                .createIfAbsent(false)
                .build());

        // ② 转入：目标库位 qty +qty。createIfAbsent = true，目标库位以前没放过这个批次就自动建行。
        //    生产日期 / 到期日期跟着源批次一起搬过去 —— 不带的话目标库位的货就丢了效期，
        //    临期预警会直接漏掉它（同一个批次，换个位置不该变成另一种精度）。
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(source.getWarehouseId())
                .locationId(dto.getToLocationId())
                .skuId(source.getSkuId())
                .batchNo(source.getBatchNo())
                .qtyChange(qty)
                .txType(InventoryTxType.TRANSFER_IN)
                .sourceType("MANUAL")
                .remark(dto.getRemark())
                .productionDate(source.getProductionDate())
                .expiryDate(source.getExpiryDate())
                .createIfAbsent(true)
                .build());

        // ③ 库位占用一减一加，必须成对做。
        //    只加不减的话，每移一次库「已用容量」就凭空多一份，看板的库位占用率会越来越离谱。
        //    减失败（计数已经不准）不阻断移库，只留日志 —— 库位计数是派生数据，不该卡住正经业务。
        locationService.decreaseUsedCapacity(source.getLocationId(), qty);
        locationService.increaseUsedCapacity(dto.getToLocationId(), qty);

        log.info("移库完成 from={} to={} skuId={} batchNo={} qty={}",
                dto.getFromLocationId(), dto.getToLocationId(), dto.getSkuId(), batchNo, qty);
    }

    /* ==================== 盘点 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakingResultVO stocktake(StocktakingDTO dto) {
        String batchNo = normalizeBatchNo(dto.getBatchNo());
        Inventory inventory = inventoryMapper.selectByBusinessKey(
                dto.getLocationId(), dto.getSkuId(), batchNo);
        if (inventory == null) {
            log.warn("盘点失败：库存不存在 locationId={} skuId={} batchNo={}",
                    dto.getLocationId(), dto.getSkuId(), batchNo);
            throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
        }

        int systemQty = nz(inventory.getQty());
        int actualQty = nz(dto.getActualQty());
        int diff = actualQty - systemQty;

        StocktakingResultVO vo = new StocktakingResultVO();
        vo.setInventoryId(inventory.getId());
        vo.setLocationId(inventory.getLocationId());
        vo.setSkuId(inventory.getSkuId());
        vo.setBatchNo(inventory.getBatchNo());
        vo.setSystemQty(systemQty);
        vo.setActualQty(actualQty);
        vo.setDiffQty(diff);

        if (diff == 0) {
            // 账实相符，什么都不用做。
            // 【不写流水】流水记的是「发生过变动」，0 变动没有信息量，
            // 写进去只会让流水表变脏、让对账的 SUM 变慢。
            // 如果业务上需要留下「什么时候盘过」的痕迹，那是盘点单 / 盘点任务该记的事，
            // 不是库存流水该记的事 —— 这两张表职责不同。
            vo.setChanged(false);
            log.info("盘点完成，账实相符 inventoryId={} qty={}", inventory.getId(), systemQty);
            return vo;
        }

        InventoryTxType txType = diff > 0
                ? InventoryTxType.STOCKTAKING_GAIN
                : InventoryTxType.STOCKTAKING_LOSS;
        vo.setChanged(true);
        vo.setTxType(txType.name());

        // 盘盈传 +diff、盘亏传 -diff（负数由 diff 的符号直接带出来）。
        // createIfAbsent = false：盘点的前提是「系统里这一行存在」，
        // 这个库位根本没登记过这批货时按「库存不存在」报错，让人先去建账，而不是凭空建一条。
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(inventory.getWarehouseId())
                .locationId(inventory.getLocationId())
                .skuId(inventory.getSkuId())
                .batchNo(inventory.getBatchNo())
                .qtyChange(diff)
                .txType(txType)
                .sourceType("STOCKTAKING")
                .remark(dto.getRemark() == null
                        ? "盘点：系统 " + systemQty + " 实盘 " + actualQty
                        : dto.getRemark())
                .createIfAbsent(false)
                .build());

        // 库位占用跟着实物数量一起变。
        // 盘盈走带容量校验的 increaseUsedCapacity：真的放不下了就报错回滚，
        // 让操作人先去调整库位容量或把货移走，而不是把库位占用悄悄写成超容状态。
        if (diff > 0) {
            locationService.increaseUsedCapacity(inventory.getLocationId(), diff);
        } else {
            locationService.decreaseUsedCapacity(inventory.getLocationId(), -diff);
        }

        log.info("盘点完成 inventoryId={} 系统={} 实盘={} 差异={} 流水类型={}",
                inventory.getId(), systemQty, actualQty, diff, txType);
        return vo;
    }

    /* ==================== 对账 ==================== */

    @Override
    public ReconcileVO reconcile(boolean autoFix) {
        // 【刻意不加 @Transactional】
        // 对账要一次扫很多行、挨个修。如果整批一个事务，第 8 行修不动就会把前 7 行的成功修正
        // 一起回滚 —— 一个「修账工具」应该是能修多少修多少。
        // 只要这里不开事务，每次 repairBalance 都是一条独立的、自带 CAS 的语句。
        List<ReconcileVO.Mismatch> mismatches = inventoryMapper.selectReconcileMismatches();

        ReconcileVO vo = new ReconcileVO();
        vo.setAutoFix(autoFix);
        vo.setCheckedCount(inventoryMapper.countInventoryRows());
        vo.setMismatchCount(mismatches.size());
        vo.setFixedCount(0);
        vo.setMismatches(mismatches);

        if (mismatches.isEmpty()) {
            log.info("对账完成，{} 条库存全部与流水一致", vo.getCheckedCount());
            return vo;
        }

        log.warn("对账发现 {} 条库存与流水不一致（autoFix={}）", mismatches.size(), autoFix);

        if (!autoFix) {
            mismatches.forEach(m -> {
                m.setFixResult("SKIPPED");
                m.setRemark("仅检查，未修正");
            });
            return vo;
        }

        int fixedCount = 0;
        for (ReconcileVO.Mismatch m : mismatches) {
            try {
                fixOne(m);
                m.setFixResult("FIXED");
                m.setRemark("已按流水重算余额：" + nz(m.getBalanceQty())
                        + " → " + nz(m.getCalculatedQty()));
                fixedCount++;
            } catch (BizException e) {
                // 修不动是可能的：扫描之后这一行又被正常业务改过（CAS 影响行数 0），
                // 这种情况记下来跳过就行 —— 下一次对账会重新发现它。
                // 关键是不能让一行修不动就把整次对账炸掉，后面的行都不修了。
                m.setFixResult("FAILED");
                m.setRemark("修正失败：" + e.getMessage());
                log.warn("对账修正失败 inventoryId={} 原因={}", m.getInventoryId(), e.getMessage());
            } catch (DataIntegrityViolationException e) {
                // 按流水算出来的数量本身就不满足库存约束（比如 锁定 + 冻结 > 在库）。
                // 这说明流水也烂了，不是重算余额能解决的问题，必须人工看。
                m.setFixResult("FAILED");
                m.setRemark("修正失败：按流水算出来的数量不满足库存约束，流水本身可能有问题，需人工核对");
                log.warn("对账修正撞到数据库约束 inventoryId={}", m.getInventoryId(), e);
            }
        }
        vo.setFixedCount(fixedCount);

        log.info("对账修正完成：不一致 {} 条，成功修正 {} 条", mismatches.size(), fixedCount);
        return vo;
    }

    /**
     * 修正一条余额：直接把它写成「按流水重算出来的值」，带 version 做 CAS。
     *
     * ========== 这里为什么要破例，为什么不走 applyChange ==========
     * 我一开始就是想走 applyChange 的（那样「所有写操作都留流水」这条规则就没有例外）。
     * 但写完测试发现它是不收敛的：
     *
     *   流水 SUM = 150，余额被改坏成 999
     *   走 applyChange 修正 → 写一条 qty_change = -849 的流水
     *   → 修正后余额 = 150（对了），但流水 SUM 变成 150 - 849 = -699（又对不上了）
     *   → 下一次对账继续报不平，再修一次又差 849 …… 永远平不了
     *
     * 根因是语义搞混了：applyChange 记的是「库存变动」，而这次没有货进出仓库，
     * 只是把一个可以推导出来的派生值重算了一遍。所以正确的做法是修数据、留日志，
     * 而不是往账本上写一笔假的变动。
     *
     * 需要更正式的审计痕迹时，应该写 audit_log（记录谁在什么时候重算了哪一行），
     * 而不是写 inventory_transaction —— 账本只记真实发生过的库存变动。
     */
    private void fixOne(ReconcileVO.Mismatch m) {
        int affected = inventoryMapper.repairBalance(
                m.getInventoryId(),
                nz(m.getCalculatedQty()),
                nz(m.getCalculatedLockedQty()),
                nz(m.getCalculatedFrozenQty()),
                nz(m.getVersion()));
        if (affected == 0) {
            throw new BizException(InventoryErrorCode.INVENTORY_CONCURRENT_MODIFY);
        }
        log.warn("对账修正 inventoryId={} 余额 {} -> {}（以流水为准）",
                m.getInventoryId(), nz(m.getBalanceQty()), nz(m.getCalculatedQty()));
    }

    /* ==================== 小工具 ==================== */

    /** 按 id 取库存行，取不到就报「库存记录不存在」 */
    private Inventory requireInventory(Long inventoryId) {
        Inventory inventory = inventoryId == null ? null : inventoryMapper.selectById(inventoryId);
        if (inventory == null) {
            log.warn("库存记录不存在 inventoryId={}", inventoryId);
            throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
        }
        return inventory;
    }

    /** 冻结 / 解冻 / 移库的数量都必须 > 0（0 是没意义，负数是方向错误） */
    private int requirePositive(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new BizException(InventoryErrorCode.INVALID_ADJUST_QTY);
        }
        return qty;
    }

    /** 和 applyChange 内部保持一致：null 统一成空串，否则唯一键会因为 null 而失效 */
    private String normalizeBatchNo(String batchNo) {
        return batchNo == null ? "" : batchNo.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /** 把库存行翻译成 applyChange 的基础入参：出库类方向统一 createIfAbsent = false */
    private InventoryChangeCommand.InventoryChangeCommandBuilder baseCommand(Inventory inventory) {
        return InventoryChangeCommand.builder()
                .warehouseId(inventory.getWarehouseId())
                .locationId(inventory.getLocationId())
                .skuId(inventory.getSkuId())
                .batchNo(inventory.getBatchNo())
                .sourceType("MANUAL")
                .createIfAbsent(false);
    }
}