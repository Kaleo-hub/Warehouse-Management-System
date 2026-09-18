package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.entity.InventoryTransaction;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.InventoryTransactionMapper;
import com.myproject.wmsserver.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发验证：证明 SELECT ... FOR UPDATE 的行锁真的挡住了并发。
 *
 * 【为什么不加 @Transactional】
 * 测试方法上的 @Transactional 会把整个测试包在【一个】事务里，
 * 而这里的并发线程走的是各自的连接、各自的事务，
 * 既看不到测试事务里没提交的数据，测试回滚也清不掉它们写的数据。
 * 所以这个类不用测试事务，改成在 @BeforeEach / @AfterEach 里物理删除测试数据。
 */
@SpringBootTest
class InventoryConcurrencyTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID = 1L;
    private static final Long SKU_ID = 1L;
    private static final String BATCH_NO = "TEST-CONCURRENT";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryTransactionMapper transactionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 物理删除测试数据。
     * 注意这里【不能】用 inventoryMapper.delete(...) —— 那会走逻辑删除（deleted=1），
     * 而唯一索引 uk_loc_sku_batch 不包含 deleted 列，删过一次之后这个 key 就再也插不进去了。
     */
    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE batch_no = ?", BATCH_NO);
        jdbcTemplate.update("DELETE FROM inventory WHERE batch_no = ?", BATCH_NO);
    }

    @Test
    @DisplayName("20 个线程同时给同一行 +1，最终必须正好是 20（行锁生效，没有丢失更新）")
    void concurrentIncreaseShouldNotLoseUpdates() throws Exception {
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                startGun.await();          // 等发令枪，尽量让 20 个线程真的同时冲
                inventoryService.applyChange(InventoryChangeCommand.builder()
                        .warehouseId(WAREHOUSE_ID)
                        .locationId(LOCATION_ID)
                        .skuId(SKU_ID)
                        .batchNo(BATCH_NO)
                        .qtyChange(1)
                        .txType(InventoryTxType.PUTAWAY_IN)
                        .sourceType("MANUAL")
                        .operatorName("并发测试")
                        .build());
                return null;
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        startGun.countDown();

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);   // 任何一个线程抛异常，这里会直接失败
        }
        pool.shutdown();

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertEquals(threads, inventory.getQty(),
                "20 次 +1 必须正好等于 20；小于 20 说明发生了丢失更新");
        assertEquals(threads, inventory.getVersion(),
                "version 应该正好被加了 20 次");

        Long txCount = transactionMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<InventoryTransaction>lambdaQuery()
                        .eq(InventoryTransaction::getBatchNo, BATCH_NO));
        assertEquals((long) threads, txCount, "每次成功变动都必须留下一条流水");
    }

    @Test
    @DisplayName("并发扣减：库存只够 10 次，20 个线程抢，最终只能是 0，不能变成负数")
    void concurrentDecreaseShouldNotGoNegative() throws Exception {
        // 先备 10 件货
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(LOCATION_ID)
                .skuId(SKU_ID)
                .batchNo(BATCH_NO)
                .qtyChange(10)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("备货")
                .build());

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                try {
                    inventoryService.applyChange(InventoryChangeCommand.builder()
                            .warehouseId(WAREHOUSE_ID)
                            .locationId(LOCATION_ID)
                            .skuId(SKU_ID)
                            .batchNo(BATCH_NO)
                            .qtyChange(-1)
                            .txType(InventoryTxType.OUTBOUND_OUT)
                            .sourceType("MANUAL")
                            .operatorName("并发扣减")
                            .build());
                    return true;      // 扣成功
                } catch (Exception e) {
                    return false;     // 库存不足被拒绝
                }
            }));
        }

        startGun.countDown();

        int success = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) success++;
        }
        pool.shutdown();

        assertEquals(10, success, "只有 10 次扣减能成功，其余必须被库存校验挡住");

        Inventory inventory = inventoryMapper.selectByBusinessKey(LOCATION_ID, SKU_ID, BATCH_NO);
        assertEquals(0, inventory.getQty(), "库存正好扣完");
        assertTrue(inventory.getQty() >= 0, "库存永远不能为负");
    }
}