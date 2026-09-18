package com.myproject.wmsserver.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.entity.InventoryTransaction;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.InventoryTransactionMapper;
import com.myproject.wmsserver.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 库存内核的验收测试。
 *
 * 跑之前确保：
 *   1. 本地 MySQL 已启动，wms 库已建好（Flyway 会自动建表）
 *   2. DB_PASSWORD 环境变量已设置
 *
 * 类上的 @Transactional 会让每个测试方法跑完自动回滚，不会污染你的数据。
 */
@SpringBootTest
@Transactional
class InventoryServiceTest {

    /** 用种子数据里已有的仓库 / 库位 / 商品，避免外键对不上 */
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID = 1L;
    private static final Long SKU_ID = 1L;
    private static final String BATCH_NO = "TEST-BATCH-001";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryTransactionMapper transactionMapper;

    /**
     * 只统计本次测试批次的流水。
     * 直接 selectCount(null) 会把库里其它数据也算进去，测试就不独立了。
     */
    private long txCount() {
        return transactionMapper.selectCount(Wrappers.<InventoryTransaction>lambdaQuery()
                .eq(InventoryTransaction::getLocationId, LOCATION_ID)
                .eq(InventoryTransaction::getSkuId, SKU_ID)
                .eq(InventoryTransaction::getBatchNo, BATCH_NO));
    }

    private InventoryChangeCommand.InventoryChangeCommandBuilder baseCommand() {
        return InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(LOCATION_ID)
                .skuId(SKU_ID)
                .batchNo(BATCH_NO)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试");
    }

    @Test
    @DisplayName("首次上架入库：自动建库存行，并写一条流水")
    void firstInbound() {
        inventoryService.applyChange(baseCommand().qtyChange(200).build());

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertNotNull(inventory, "库存行应该被自动创建");
        assertEquals(200, inventory.getQty());
        assertEquals(0, inventory.getLockedQty());
        assertEquals(200, inventory.getAvailableQty(), "可用数量应该由数据库生成列算出来");
        assertEquals(1, inventory.getVersion(), "version 应该 +1");
        assertNotNull(inventory.getInboundTime(), "首次入库要记 inbound_time，FIFO 靠它");

        assertEquals(1L, txCount(), "必须写一条流水（BR-008）");
    }

    @Test
    @DisplayName("同一库位+SKU+批次只允许一条库存行（BR-003）")
    void sameKeyOnlyOneRow() {
        inventoryService.applyChange(baseCommand().qtyChange(100).build());
        inventoryService.applyChange(baseCommand().qtyChange(50).build());

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertEquals(150, inventory.getQty(), "两次入库应该累加到同一条记录上");
        assertEquals(2L, txCount(), "两次变动写两条流水");
    }

    @Test
    @DisplayName("扣成负数应该被拒绝，并且整笔回滚")
    void cannotGoNegative() {
        inventoryService.applyChange(baseCommand().qtyChange(100).build());

        BizException ex = assertThrows(BizException.class, () ->
                inventoryService.applyChange(baseCommand()
                        .qtyChange(-101)
                        .txType(InventoryTxType.OUTBOUND_OUT)
                        .build()));
        assertEquals(InventoryErrorCode.STOCK_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertEquals(100, inventory.getQty(), "失败的变动不能改到库存");
        assertEquals(1L, txCount(), "失败的变动不能留下流水");
    }

    @Test
    @DisplayName("可用库存不足时，分配锁定应该失败（BR-002）")
    void cannotLockMoreThanAvailable() {
        inventoryService.applyChange(baseCommand().qtyChange(100).build());

        // 冻结 30 后只剩 70 可用
        inventoryService.applyChange(baseCommand()
                .frozenChange(30)
                .txType(InventoryTxType.FREEZE)
                .build());

        BizException ex = assertThrows(BizException.class, () ->
                inventoryService.applyChange(baseCommand()
                        .lockedChange(71)
                        .txType(InventoryTxType.ALLOCATE_LOCK)
                        .build()));
        assertEquals(InventoryErrorCode.AVAILABLE_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertEquals(0, inventory.getLockedQty());
        assertEquals(70, inventory.getAvailableQty());
    }

    @Test
    @DisplayName("流水要记清楚变动前后，方便对账")
    void transactionRecordsBeforeAndAfter() {
        inventoryService.applyChange(baseCommand().qtyChange(80).build());
        inventoryService.applyChange(baseCommand().qtyChange(20).build());

        var transactions = transactionMapper.selectList(Wrappers.<InventoryTransaction>lambdaQuery()
                .eq(InventoryTransaction::getLocationId, LOCATION_ID)
                .eq(InventoryTransaction::getSkuId, SKU_ID)
                .eq(InventoryTransaction::getBatchNo, BATCH_NO)
                .orderByAsc(InventoryTransaction::getId));
        assertEquals(2, transactions.size());

        InventoryTransaction tx = transactions.get(1);
        assertEquals(80, tx.getQtyBefore());
        assertEquals(100, tx.getQtyAfter());
        assertEquals(20, tx.getQtyChange());
        assertEquals("PUTAWAY_IN", tx.getTxType());
        assertNotNull(tx.getTxNo());
    }

    @Test
    @DisplayName("三个变动量全为 0 的调用没有意义，应该拒绝")
    void rejectEmptyChange() {
        assertThrows(BizException.class, () ->
                inventoryService.applyChange(baseCommand().build()));
    }
}