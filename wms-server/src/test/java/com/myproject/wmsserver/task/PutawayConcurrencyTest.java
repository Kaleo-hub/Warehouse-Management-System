package com.myproject.wmsserver.task;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inbound.dto.InboundItemDTO;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.dto.ReceiveItemDTO;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上架任务的并发验证 —— 对应 docs/02 面试亮点 #4「状态机 + CAS」。
 *
 * 【为什么不加 @Transactional】
 * 测试事务会把整个测试包在一个事务里，而并发线程走各自的连接、各自的事务，
 * 看不到测试事务未提交的数据，测试回滚也清不掉线程写的数据。
 * 所以这里不用测试事务，改成 @AfterEach 里物理清理。
 */
@SpringBootTest
class PutawayConcurrencyTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long SKU_ID = 1L;
    private static final String BATCH_NO = "B-PUTAWAY-CONCURRENT";

    @Autowired
    private InboundOrderService inboundOrderService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 记下来给 @AfterEach 清理用 */
    private Long createdOrderId;
    private Long touchedLocationId;
    private Integer originalUsedCapacity;

    @AfterEach
    void cleanup() {
        if (createdOrderId != null) {
            jdbcTemplate.update("DELETE FROM inventory_transaction WHERE batch_no = ?", BATCH_NO);
            jdbcTemplate.update("DELETE FROM inventory WHERE batch_no = ?", BATCH_NO);
            jdbcTemplate.update(
                    "DELETE FROM stock_task WHERE source_type = 'INBOUND_ORDER' AND source_id = ?", createdOrderId);
            jdbcTemplate.update("DELETE FROM inbound_order_item WHERE order_id = ?", createdOrderId);
            jdbcTemplate.update("DELETE FROM inbound_order WHERE id = ?", createdOrderId);
            createdOrderId = null;
        }
        // 上架会占用库位容量，测完还原，免得影响别的用例
        if (touchedLocationId != null && originalUsedCapacity != null) {
            jdbcTemplate.update("UPDATE location SET used_capacity = ? WHERE id = ?",
                    originalUsedCapacity, touchedLocationId);
            touchedLocationId = null;
            originalUsedCapacity = null;
        }
    }

    /* ---------------------------- 准备数据 ---------------------------- */

    /** 建单 → 收货 → 生成上架任务，返回处于 CREATED 的任务 */
    private StockTaskVO preparePutawayTask(int qty) {
        InboundOrderSaveDTO save = new InboundOrderSaveDTO();
        save.setWarehouseId(WAREHOUSE_ID);
        save.setSupplierName("并发测试供应商");
        InboundItemDTO item = new InboundItemDTO();
        item.setSkuId(SKU_ID);
        item.setExpectQty(qty);
        save.setItems(List.of(item));

        createdOrderId = inboundOrderService.create(save);
        InboundOrderVO order = inboundOrderService.get(createdOrderId);

        ReceiveItemDTO recvItem = new ReceiveItemDTO();
        recvItem.setItemId(order.getItems().get(0).getId());
        recvItem.setReceivedQty(qty);
        recvItem.setBatchNo(BATCH_NO);
        InboundReceiveDTO recv = new InboundReceiveDTO();
        recv.setItems(List.of(recvItem));
        inboundOrderService.receive(createdOrderId, recv);

        stockTaskService.generatePutawayTasks(createdOrderId);

        StockTaskQuery query = new StockTaskQuery();
        query.setSourceNo(order.getOrderNo());
        PageResult<StockTaskVO> page = stockTaskService.page(query);
        StockTaskVO task = page.getRecords().get(0);

        touchedLocationId = task.getToLocationId();
        originalUsedCapacity = jdbcTemplate.queryForObject(
                "SELECT used_capacity FROM location WHERE id = ?", Integer.class, touchedLocationId);

        return task;
    }

    private int inventoryQty(Long locationId, Long skuId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, BATCH_NO);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int transactionCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE batch_no = ?", Integer.class, BATCH_NO);
        return n == null ? 0 : n;
    }

    /* ---------------------------- 测试 ---------------------------- */

    @Test
    @DisplayName("★ 10 个线程同时完成同一个上架任务：只有 1 次成功，库存只加一次")
    void concurrentCompleteShouldWriteInventoryOnce() throws Exception {
        StockTaskVO task = preparePutawayTask(100);
        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                try {
                    StockTaskCompleteDTO dto = new StockTaskCompleteDTO();
                    dto.setActualQty(100);
                    stockTaskService.complete(task.getId(), dto);
                    success.incrementAndGet();
                } catch (Exception e) {
                    // 状态已经被别人改掉了，CAS 影响行数 0 → 70005
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

        assertEquals(1, success.get(), "10 个并发请求只允许 1 个成功完成");
        assertEquals(threads - 1, rejected.get(), "其余 9 个必须被状态机挡住");

        assertEquals(100, inventoryQty(task.getToLocationId(), task.getSkuId()),
                "库存只能加一次 —— 加了 200 说明状态占位没起作用");
        assertEquals(1, transactionCount(), "流水也只能有 1 条");
    }

    @Test
    @DisplayName("★ 10 个作业员同时领取同一个任务：只有 1 个人领到")
    void concurrentAssignShouldHaveSingleWinner() throws Exception {
        StockTaskVO task = preparePutawayTask(50);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                try {
                    stockTaskService.assign(task.getId());
                    success.incrementAndGet();
                } catch (Exception e) {
                    // 被人抢走了 → 70003 任务已被领取
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

        assertEquals(1, success.get(), "任务只能被领取一次");
        assertEquals(threads - 1, rejected.get());

        StockTaskVO after = stockTaskService.get(task.getId());
        assertEquals("ASSIGNED", after.getStatus());
        assertTrue(after.getAssigneeId() != null || after.getAssigneeName() != null,
                "应该记录了领取人");
    }
}