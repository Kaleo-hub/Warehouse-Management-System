package com.myproject.wmsserver.inventory.service.impl;

import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.entity.InventoryTransaction;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.InventoryTransactionMapper;
import com.myproject.wmsserver.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 库存写入内核 —— 整个 WMS 最核心的一段代码，出库 / 移库 / 盘点全部复用它。
 *
 * 执行流程（五步，顺序不能乱）：
 *
 *   ① 确保库存行存在        INSERT ... ON DUPLICATE KEY UPDATE   （BR-003）
 *   ② 锁住这一行            SELECT ... FOR UPDATE
 *   ③ 先算后校验            不能先写库再检查
 *   ④ 更新库存（CAS）        UPDATE ... WHERE version = ?
 *   ⑤ 写流水                INSERT inventory_transaction          （BR-008）
 *
 * ①③④⑤ 全在同一个事务里，任何一步抛异常，整笔回滚，不会出现「改了库存没流水」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final DateTimeFormatter TX_NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyChange(InventoryChangeCommand command) {
        // ---------- 0. 入参校验 + 归一化 ----------
        validate(command);

        Long warehouseId = command.getWarehouseId();
        Long locationId = command.getLocationId();
        Long skuId = command.getSkuId();
        // 批次号统一成空串：唯一键含这一列，null 会让唯一约束失效
        String batchNo = normalizeBatchNo(command.getBatchNo());

        // ---------- ① 确保库存行存在（仅入库类变动）----------
        // 不存在就建一条全 0 的；已存在则什么都不改。目的是让下一步能锁到行。
        // 出库类变动传 createIfAbsent = false：没有库存行说明这个库位本来就没货，
        // 直接报库存不足，而不是先建一条 0 库存再失败（那样会留下垃圾数据）。
        if (command.isCreateIfAbsent()) {
            inventoryMapper.ensureRow(warehouseId, locationId, skuId, batchNo,
                    command.getProductionDate(), command.getExpiryDate());
        }

        // ---------- ② 锁行，拿当前值 ----------
        Inventory inventory = inventoryMapper.selectForUpdate(locationId, skuId, batchNo);
        if (inventory == null) {
            // createIfAbsent = false 且这一行不存在 → 这个库位根本没有这批货
            log.warn("库存记录不存在 type={} locationId={} skuId={} batchNo={}",
                    command.getTxType(), locationId, skuId, batchNo);
            throw new BizException(InventoryErrorCode.STOCK_NOT_ENOUGH);
        }

        int qtyBefore = nz(inventory.getQty());
        int lockedBefore = nz(inventory.getLockedQty());
        int frozenBefore = nz(inventory.getFrozenQty());

        // ---------- ③ 先算，再校验，最后才落库 ----------
        int qtyAfter = qtyBefore + command.getQtyChange();
        int lockedAfter = lockedBefore + command.getLockedChange();
        int frozenAfter = frozenBefore + command.getFrozenChange();

        checkInvariant(qtyAfter, lockedAfter, frozenAfter, command, locationId, skuId, batchNo);

        // ---------- ④ 更新库存（带 version CAS）----------
        int affected = inventoryMapper.updateQty(
                inventory.getId(), qtyAfter, lockedAfter, frozenAfter, nz(inventory.getVersion()));
        if (affected == 0) {
            // FOR UPDATE 已经排除了并发，这里是第二道防线
            log.warn("库存 CAS 更新失败 locationId={} skuId={} batchNo={}", locationId, skuId, batchNo);
            throw new BizException(InventoryErrorCode.INVENTORY_CONCURRENT_MODIFY);
        }

        // ---------- ⑤ 写流水 ----------
        InventoryTransaction tx = buildTransaction(
                command, inventory, batchNo, qtyBefore, qtyAfter, lockedBefore, frozenBefore);
        transactionMapper.insert(tx);

        log.info("库存变动完成 txNo={} type={} 库位={} SKU={} 批次={} qty {} -> {}",
                tx.getTxNo(), command.getTxType(), locationId, skuId,
                batchNo.isEmpty() ? "(无)" : batchNo, qtyBefore, qtyAfter);
    }

    /**
     * 不变量校验（BR-001 / BR-002）。
     * 三个数量任何时刻都不能为负；locked + frozen 不能超过 qty，也就是可用数量不能为负。
     */
    private void checkInvariant(int qty, int locked, int frozen,
                                InventoryChangeCommand command,
                                Long locationId, Long skuId, String batchNo) {
        if (qty < 0 || locked < 0 || frozen < 0) {
            log.warn("库存不足，拒绝变动 type={} locationId={} skuId={} batchNo={} 结果 qty={} locked={} frozen={}",
                    command.getTxType(), locationId, skuId, batchNo, qty, locked, frozen);
            throw new BizException(InventoryErrorCode.STOCK_NOT_ENOUGH);
        }
        if (locked + frozen > qty) {
            log.warn("可用库存不足，拒绝变动 type={} locationId={} skuId={} batchNo={} 可用={}",
                    command.getTxType(), locationId, skuId, batchNo, qty - locked - frozen);
            throw new BizException(InventoryErrorCode.AVAILABLE_NOT_ENOUGH);
        }
    }

    private void validate(InventoryChangeCommand command) {
        if (command == null
                || command.getWarehouseId() == null
                || command.getLocationId() == null
                || command.getSkuId() == null) {
            throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
        }
        if (command.getTxType() == null) {
            throw new BizException(InventoryErrorCode.INVALID_QTY_CHANGE);
        }
        if (command.getQtyChange() == 0
                && command.getLockedChange() == 0
                && command.getFrozenChange() == 0) {
            throw new BizException(InventoryErrorCode.INVALID_QTY_CHANGE);
        }
    }

    private InventoryTransaction buildTransaction(InventoryChangeCommand command,
                                                   Inventory inventory,
                                                   String batchNo,
                                                   int qtyBefore, int qtyAfter,
                                                   int lockedBefore, int frozenBefore) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTxNo(generateTxNo());
        tx.setWarehouseId(inventory.getWarehouseId());
        tx.setLocationId(inventory.getLocationId());
        tx.setSkuId(inventory.getSkuId());
        tx.setBatchNo(batchNo);
        tx.setTxType(command.getTxType().name());
        tx.setQtyChange(command.getQtyChange());
        tx.setLockedChange(command.getLockedChange());
        tx.setFrozenChange(command.getFrozenChange());
        // 变动前后必须记清楚：对账时就是看相邻流水是否连续
        tx.setQtyBefore(qtyBefore);
        tx.setQtyAfter(qtyAfter);
        tx.setSourceType(command.getSourceType());
        tx.setSourceId(command.getSourceId());
        tx.setSourceNo(command.getSourceNo());
        tx.setOperatorId(resolveOperatorId(command));
        tx.setOperatorName(resolveOperatorName(command));
        tx.setRemark(command.getRemark());
        tx.setCreateTime(LocalDateTime.now());
        return tx;
    }

    /**
     * 流水号：TX + 毫秒时间戳 + 3 位随机。
     * 表上有 uk_tx_no 唯一索引兜底，重复了会直接报错，不会写脏。
     * 生产环境建议换成号段 / 雪花算法，学习项目这样够用。
     */
    private String generateTxNo() {
        return "TX" + LocalDateTime.now().format(TX_NO_FORMAT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private Long resolveOperatorId(InventoryChangeCommand command) {
        return command.getOperatorId() != null ? command.getOperatorId() : UserContext.getUserId();
    }

    private String resolveOperatorName(InventoryChangeCommand command) {
        if (command.getOperatorName() != null && !command.getOperatorName().isBlank()) {
            return command.getOperatorName();
        }
        UserContext.CurrentUser user = UserContext.get();
        return user == null ? "" : user.getUsername();
    }

    private String normalizeBatchNo(String batchNo) {
        return batchNo == null ? "" : batchNo.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}