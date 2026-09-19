package com.myproject.wmsserver.outbound;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.outbound.dto.OutboundItemDTO;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 出库的「整单原子」和「并发不超卖」验证。
 *
 * 【为什么不加 @Transactional】
 * 两个原因：
 *   1. 测试事务里，Service 抛异常只是把事务标记成 rollback-only，
 *      回滚发生在测试结束，测试中间读到的还是"回滚前"的数据 —— 验不出原子性。
 *   2. 并发线程走各自的连接和事务，看不到测试事务未提交的数据。
 * 所以这里不用测试事务，改成 @AfterEach 里物理清理。
 */
@SpringBootTest
class OutboundRollbackTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_A = 1L;
    private static final String BATCH = "OUT-ROLLBACK-TEST";

    /**
     * 专用 SKU：FIFO 是按 (仓库, SKU) 全局排序的，
     * 用真实 SKU 会被库里已有的更早批次干扰，测试就不独立了。
     */
    private static final String TEST_SKU_CODE = "TEST-OUT-ROLLBACK-SKU";

    private Long testSkuId;

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void createTestSku() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id IN "
                + "(SELECT id FROM product WHERE sku_code = ?)", TEST_SKU_CODE);
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id IN "
                + "(SELECT id FROM product WHERE sku_code = ?)", TEST_SKU_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, shelf_life_days, status) "
                + "VALUES (?, '出库回滚测试商品', '件', 365, 'ENABLED')", TEST_SKU_CODE);
        testSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, TEST_SKU_CODE);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE batch_no = ?", BATCH);
        jdbcTemplate.update("DELETE FROM inventory WHERE batch_no = ?", BATCH);
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM outbound_allocate WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM outbound_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM outbound_order WHERE id = ?", orderId);
        }
        createdOrderIds.clear();

        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
    }

    private void stockIn(int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(LOCATION_A)
                .skuId(testSkuId)
                .batchNo(BATCH)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试备货")
                .build());
    }

    private Long createOrder(int qty) {
        OutboundOrderSaveDTO dto = new OutboundOrderSaveDTO();
        dto.setWarehouseId(WAREHOUSE_ID);
        dto.setCustomerName("并发测试客户");
        OutboundItemDTO item = new OutboundItemDTO();
        item.setSkuId(testSkuId);
        item.setQty(qty);
        dto.setItems(List.of(item));

        Long id = outboundOrderService.create(dto);
        createdOrderIds.add(id);
        return id;
    }

    private int lockedQty() {
        Integer locked = jdbcTemplate.queryForObject(
                "SELECT locked_qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, LOCATION_A, testSkuId, BATCH);
        return locked == null ? 0 : locked;
    }

    private int qty() {
        Integer qty = jdbcTemplate.queryForObject(
                "SELECT qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, LOCATION_A, testSkuId, BATCH);
        return qty == null ? 0 : qty;
    }

    @Test
    @DisplayName("★ 整单原子：库存不够时整单回滚，先锁上的也要还回去")
    void allocateShouldRollbackWhenNotEnough() {
        stockIn(30);
        Long orderId = createOrder(100);

        BizException ex = assertThrows(BizException.class, () -> outboundOrderService.allocate(orderId));
        assertEquals(OutboundErrorCode.STOCK_NOT_ENOUGH_FOR_ALLOCATE.getCode(),
                ex.getErrorCode().getCode());

        // 这里能真正验出回滚：Service 自己的事务已经回滚并提交了"什么都没发生"
        assertEquals(0, lockedQty(), "失败的分配不能留下 locked_qty");
        assertEquals(30, qty(), "qty 更不能变");
        assertEquals(OutboundStatus.CREATED.name(), outboundOrderService.get(orderId).getStatus());
        assertEquals(0, outboundOrderService.get(orderId).getAllocates().size(),
                "失败的分配不能留下分配明细");

        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE tx_type = 'ALLOCATE_LOCK' AND batch_no = ?",
                Integer.class, BATCH);
        assertEquals(0, txCount, "失败的分配不能留下库存流水");
    }

    @Test
    @DisplayName("★ 3 张单并发抢同一批库存（共 100 件，每单要 50）：只能成功 2 张，绝不超卖")
    void concurrentAllocateShouldNotOversell() throws Exception {
        stockIn(100);

        int threads = 3;
        int qtyPerOrder = 50;

        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            orderIds.add(createOrder(qtyPerOrder));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (Long orderId : orderIds) {
            futures.add(pool.submit(() -> {
                startGun.await();
                try {
                    outboundOrderService.allocate(orderId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    // 库存被前面的事务锁走之后，applyChange 重算可用量发现不够 → 60003
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        startGun.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(2, success.get(), "100 件库存只够 2 张单分配");
        assertEquals(1, rejected.get(), "第 3 张必须被库存校验挡住");

        assertEquals(100, lockedQty(), "锁定量最多就是全部库存，绝不能超卖");
        assertEquals(100, qty(), "分配阶段 qty 不能变");

        Integer lockTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE tx_type = 'ALLOCATE_LOCK' AND batch_no = ?",
                Integer.class, BATCH);
        assertEquals(2, lockTxCount, "只有成功的 2 次分配才该留下流水");
    }
}