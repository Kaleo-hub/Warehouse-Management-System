package com.myproject.wmsserver.outbound;

import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.outbound.dto.OutboundItemDTO;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.task.job.OutboundTimeoutJob;
import com.myproject.wmsserver.task.service.StockTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BR-010 验收测试：分配后超时未处理的出库单要自动释放。
 *
 * 【为什么不用 @Transactional】
 * 超时释放是「定时任务读一遍、逐单开自己的事务去释放」，
 * 测试自己开着事务的话，定时任务里那些改动会被包进测试事务，
 * 既看不到真实提交，也没法验证「释放确实落库了」。
 * 所以这里自建独立 SKU，@AfterEach 物理清理。
 *
 * 【为什么要手工把 allocate_time 改早】
 * 等 30 分钟不现实。直接改数据库把「分配时间」往前拨，
 * 比给生产代码开一个「测试专用超时参数」干净 —— 不为测试改业务代码。
 */
@SpringBootTest
class OutboundTimeoutReleaseTest {

    private static final String TEST_SKU_CODE = "TEST-TIMEOUT-SKU";
    private static final String BATCH_NO = "TIMEOUT-TEST";
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID = 1L;

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OutboundTimeoutJob outboundTimeoutJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testSkuId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, shelf_life_days, status) "
                + "VALUES (?, '超时释放测试商品', '件', 365, 'ENABLED')", TEST_SKU_CODE);
        testSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, TEST_SKU_CODE);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (orderId != null) {
            jdbcTemplate.update("DELETE FROM stock_task WHERE source_type = 'OUTBOUND_ORDER' AND source_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM outbound_allocate WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM outbound_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM outbound_order WHERE id = ?", orderId);
            orderId = null;
        }
        if (testSkuId != null) {
            jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id = ?", testSkuId);
            jdbcTemplate.update("DELETE FROM inventory WHERE sku_id = ?", testSkuId);
        }
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
    }

    /* ---------------------------- 工具 ---------------------------- */

    private void stockIn(int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(LOCATION_ID)
                .skuId(testSkuId)
                .batchNo(BATCH_NO)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试备货")
                .build());
    }

    private void createOrder(int qty) {
        OutboundOrderSaveDTO dto = new OutboundOrderSaveDTO();
        dto.setWarehouseId(WAREHOUSE_ID);
        dto.setCustomerName("超时测试客户");
        dto.setReceiveAddress("上海市某某路 1 号");

        OutboundItemDTO item = new OutboundItemDTO();
        item.setSkuId(testSkuId);
        item.setQty(qty);
        dto.setItems(List.of(item));

        orderId = outboundOrderService.create(dto);
    }

    /** 返回 [qty, locked, available] */
    private int[] inventory() {
        return jdbcTemplate.queryForObject(
                "SELECT qty, locked_qty, available_qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                (rs, i) -> new int[]{rs.getInt("qty"), rs.getInt("locked_qty"), rs.getInt("available_qty")},
                LOCATION_ID, testSkuId, BATCH_NO);
    }

    /** 把分配时间往前拨，模拟「已经放了很久没人管」 */
    private void backdateAllocate(int minutes) {
        jdbcTemplate.update("UPDATE outbound_order SET allocate_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutes), orderId);
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_order WHERE id = ?", String.class, orderId);
    }

    private int unfinishedPickTaskCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_task WHERE source_type = 'OUTBOUND_ORDER' AND source_id = ? "
                        + "AND status NOT IN ('DONE', 'CANCELLED')", Integer.class, orderId);
        return n == null ? 0 : n;
    }

    /* ---------------------------- 用例 ---------------------------- */

    @Test
    @DisplayName("★ BR-010：分配后超时未处理的出库单，自动释放锁定库存并回到 CREATED")
    void timeoutAllocatedOrderShouldBeReleased() {
        stockIn(100);
        createOrder(40);
        outboundOrderService.allocate(orderId);

        // 分配后：库存没动，40 件被锁住（货还在货架上，但别人用不了）
        int[] allocated = inventory();
        assertEquals(100, allocated[0]);
        assertEquals(40, allocated[1]);
        assertEquals(60, allocated[2]);
        assertEquals(OutboundStatus.ALLOCATED.name(), orderStatus());

        // 把分配时间往前拨 60 分钟（默认阈值 30 分钟）
        backdateAllocate(60);

        outboundTimeoutJob.releaseTimeoutAllocations();

        int[] released = inventory();
        assertEquals(100, released[0], "释放不动 qty —— 货一直在货架上");
        assertEquals(0, released[1], "locked_qty 必须归零");
        assertEquals(100, released[2], "可用数量全部还回来");
        assertEquals(OutboundStatus.CREATED.name(), orderStatus(), "单据回到 CREATED，可以重新分配或取消");
    }

    @Test
    @DisplayName("还没超时的分配不能被释放（不能把正常的单放掉）")
    void freshAllocationShouldNotBeReleased() {
        stockIn(100);
        createOrder(40);
        outboundOrderService.allocate(orderId);

        // 不拨时间：刚分配完，离超时还早
        outboundTimeoutJob.releaseTimeoutAllocations();

        assertEquals(OutboundStatus.ALLOCATED.name(), orderStatus());
        assertEquals(40, inventory()[1], "锁定还在 —— 没超时就不该动");
    }

    @Test
    @DisplayName("★ 已经开始拣货（PICKING）的单不会被超时释放 —— 货可能已经被拿下来了")
    void pickingOrderShouldNotBeReleased() {
        stockIn(100);
        createOrder(40);
        outboundOrderService.allocate(orderId);
        stockTaskService.generatePickTasks(orderId);

        assertEquals(OutboundStatus.PICKING.name(), orderStatus(), "生成拣货任务后单据进入 PICKING");
        int[] before = inventory();

        backdateAllocate(60);
        outboundTimeoutJob.releaseTimeoutAllocations();

        assertEquals(OutboundStatus.PICKING.name(), orderStatus(),
                "PICKING 不可逆：货可能已经从货架上拿下来了，释放锁定会造成账实不符");
        assertEquals(before[1], inventory()[1], "锁定不能被放掉");
        assertEquals(1, unfinishedPickTaskCount(), "作业员手上的任务也不能被作废");
    }
}