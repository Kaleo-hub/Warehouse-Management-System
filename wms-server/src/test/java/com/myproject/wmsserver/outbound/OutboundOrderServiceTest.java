package com.myproject.wmsserver.outbound;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;
import com.myproject.wmsserver.common.exception.TaskErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.outbound.dto.OutboundItemDTO;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.outbound.vo.OutboundAllocateVO;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出库链路验收测试：建单 → FIFO 分配 → 拣货 → 发货。
 *
 * 重点验证三件事：
 *   1. FIFO 按批次拆分（BR-004）
 *   2. 分配只锁 locked_qty，qty 不变
 *   3. ★ 拣货完全不碰库存，发货才核销
 */
@SpringBootTest
@Transactional
class OutboundOrderServiceTest {

    private static final Long WAREHOUSE_ID = 1L;
    /**
     * 【为什么用专用 SKU】
     * FIFO 是按 (仓库, SKU) 全局排序的：只要库里存在同一个 SKU 更早入库的批次，
     * 分配就会先去拿它，测试的断言就崩了。
     * 所以每个用例自己造一个全新的 SKU，保证 FIFO 候选里只有本用例的数据。
     */
    private static final String TEST_SKU_CODE = "TEST-OUT-SKU";

    private Long testSkuId;
    /** 种子里已有的两个库位，用它们放不同批次的货 */
    private static final Long LOCATION_A = 1L;
    private static final Long LOCATION_B = 3L;
    private static final String BATCH_A = "OUT-TEST-A";
    private static final String BATCH_B = "OUT-TEST-B";

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTestSku() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id IN "
                + "(SELECT id FROM product WHERE sku_code = ?)", TEST_SKU_CODE);
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id IN "
                + "(SELECT id FROM product WHERE sku_code = ?)", TEST_SKU_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, shelf_life_days, status) "
                + "VALUES (?, '出库测试商品', '件', 365, 'ENABLED')", TEST_SKU_CODE);
        testSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, TEST_SKU_CODE);
    }

    @AfterEach
    void dropTestSku() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
    }

    /* ---------------------------- 工具 ---------------------------- */

    /** 库位已用容量：派生计数，应该等于这个库位上所有库存行的 qty 之和 */
    private int usedCapacity(Long locationId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT used_capacity FROM location WHERE id = ?", Integer.class, locationId);
        return v == null ? 0 : v;
    }

    /** 模拟上架入库：往指定库位加一批货 */
    private void stockIn(Long locationId, String batchNo, int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(locationId)
                .skuId(testSkuId)
                .batchNo(batchNo)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试备货")
                .build());
    }

    private Long createOrder(int qty) {
        OutboundOrderSaveDTO dto = new OutboundOrderSaveDTO();
        dto.setWarehouseId(WAREHOUSE_ID);
        dto.setCustomerName("测试客户");
        dto.setReceiveAddress("上海市某某路 1 号");

        OutboundItemDTO item = new OutboundItemDTO();
        item.setSkuId(testSkuId);
        item.setQty(qty);
        dto.setItems(List.of(item));

        return outboundOrderService.create(dto);
    }

    /**
     * 走完整的拣货流程：生成拣货任务（订单 → PICKING）→ 逐条领取/开始/完成。
     * 这样单据才能推进到 PICKED，才能发货。
     */
    private void pickAll(Long orderId) {
        stockTaskService.generatePickTasks(orderId);

        StockTaskQuery query = new StockTaskQuery();
        query.setSourceNo(outboundOrderService.get(orderId).getOrderNo());
        query.setTaskType("PICK");

        for (StockTaskVO task : stockTaskService.page(query).getRecords()) {
            stockTaskService.assign(task.getId());
            stockTaskService.start(task.getId());
            StockTaskCompleteDTO dto = new StockTaskCompleteDTO();
            dto.setActualQty(task.getPlanQty());
            stockTaskService.complete(task.getId(), dto);
        }
    }

    /** 取某条库存的三个数量 */
    private int[] inventoryOf(Long locationId, String batchNo) {
        return jdbcTemplate.queryForObject(
                "SELECT qty, locked_qty, available_qty FROM inventory "
                        + "WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                (rs, rowNum) -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)},
                locationId, testSkuId, batchNo);
    }

    /* ---------------------------- 建单 ---------------------------- */

    @Test
    @DisplayName("建单：生成 OUT 单号、汇总需求量、SKU 快照、状态 CREATED")
    void createOrder() {
        Long id = createOrder(50);
        OutboundOrderVO vo = outboundOrderService.get(id);

        assertTrue(vo.getOrderNo().startsWith("OUT"), "出库单号应以 OUT 开头");
        assertEquals(17, vo.getOrderNo().length(), "OUT(3) + 日期(8) + 序号(6) = 17 位");
        assertEquals(OutboundStatus.CREATED.name(), vo.getStatus());
        assertEquals(50, vo.getTotalQty());
        assertEquals(0, vo.getAllocatedQty());
        assertEquals(TEST_SKU_CODE, vo.getItems().get(0).getSkuCode(), "skuCode 要落快照");
        assertNotNull(vo.getItems().get(0).getSkuName());
        assertTrue(vo.getAllocates().isEmpty(), "还没分配，不应有分配明细");
    }

    /* ---------------------------- 分配 ★ ---------------------------- */

    @Test
    @DisplayName("★ FIFO 分配：入库早的批次先被分配，不够才拆到下一批")
    void allocateShouldFollowFifo() {
        // 先入 A（30 件），再入 B（50 件）—— A 的 inbound_time 更早
        stockIn(LOCATION_A, BATCH_A, 30);
        stockIn(LOCATION_B, BATCH_B, 50);

        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        OutboundOrderVO vo = outboundOrderService.get(orderId);
        assertEquals(OutboundStatus.ALLOCATED.name(), vo.getStatus());
        assertEquals(40, vo.getAllocatedQty());

        List<OutboundAllocateVO> allocates = vo.getAllocates();
        assertEquals(2, allocates.size(), "40 件应该拆成 A 库位 30 + B 库位 10");

        // 第一条必须是入库更早的 A 批次
        assertEquals(BATCH_A, allocates.get(0).getBatchNo(), "FIFO 应该先分配入库早的批次");
        assertEquals(LOCATION_A, allocates.get(0).getLocationId());
        assertEquals(30, allocates.get(0).getAllocatedQty());

        assertEquals(BATCH_B, allocates.get(1).getBatchNo());
        assertEquals(10, allocates.get(1).getAllocatedQty());
    }

    @Test
    @DisplayName("分配只锁 locked_qty，qty 和 available 的变化要符合三步模型")
    void allocateShouldOnlyLockQuantity() {
        stockIn(LOCATION_A, BATCH_A, 100);

        int[] before = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(100, before[0]);
        assertEquals(0, before[1]);
        assertEquals(100, before[2]);

        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        int[] after = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(100, after[0], "分配阶段 qty 不能变 —— 货还在货架上");
        assertEquals(40, after[1], "locked_qty 应该 +40");
        assertEquals(60, after[2], "可用量应该减到 60");
    }


    @Test
    @DisplayName("没有库存时分配 → 60003，且不能凭空建出 0 库存的库存行")
    void allocateWithoutStockShouldNotCreateInventoryRow() {
        Long orderId = createOrder(10);

        assertThrows(BizException.class, () -> outboundOrderService.allocate(orderId));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE sku_id = ? AND batch_no LIKE 'OUT-TEST%'",
                Integer.class, testSkuId);
        assertEquals(0, rows, "出库绝不应该创建库存行（createIfAbsent=false 的意义）");
    }

    @Test
    @DisplayName("已分配的单据不能重复分配")
    void cannotAllocateTwice() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        BizException ex = assertThrows(BizException.class, () -> outboundOrderService.allocate(orderId));
        assertEquals(OutboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 释放 ---------------------------- */

    @Test
    @DisplayName("释放分配：locked_qty 还回去，单据回到 CREATED")
    void releaseShouldRestoreLocked() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        assertEquals(40, inventoryOf(LOCATION_A, BATCH_A)[1]);

        outboundOrderService.release(orderId);

        int[] after = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(0, after[1], "释放后 locked_qty 应该归零");
        assertEquals(100, after[2], "可用量应该完全恢复");

        OutboundOrderVO vo = outboundOrderService.get(orderId);
        assertEquals(OutboundStatus.CREATED.name(), vo.getStatus());
        assertEquals(0, vo.getAllocatedQty());
    }

    /* ---------------------------- 取消 ---------------------------- */

    @Test
    @DisplayName("CREATED 可以取消；已分配的不能取消")
    void cancelRules() {
        Long orderId = createOrder(10);
        outboundOrderService.cancel(orderId);
        assertEquals(OutboundStatus.CANCELLED.name(), outboundOrderService.get(orderId).getStatus());

        stockIn(LOCATION_A, BATCH_A, 100);
        Long allocated = createOrder(40);
        outboundOrderService.allocate(allocated);

        BizException ex = assertThrows(BizException.class, () -> outboundOrderService.cancel(allocated));
        assertEquals(OutboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 发货 ---------------------------- */

    @Test
    @DisplayName("★ 发货核销：qty 和 locked_qty 一起减，available 保持不变")
    void shipShouldWriteOffInventory() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        // 分配后：qty=100 locked=40 available=60
        int[] allocated = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(100, allocated[0]);
        assertEquals(40, allocated[1]);
        assertEquals(60, allocated[2]);

        // 走完整拣货流程：生成拣货任务 → 领取/开始/完成 → 单据推进到 PICKED
        pickAll(orderId);
        assertEquals(OutboundStatus.PICKED.name(), outboundOrderService.get(orderId).getStatus(),
                "全部拣完单据应该到 PICKED");

        outboundOrderService.ship(orderId);

        int[] shipped = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(60, shipped[0], "发货后 qty 应该减 40");
        assertEquals(0, shipped[1], "发货后 locked_qty 应该归零");
        assertEquals(60, shipped[2], "可用量保持不变 —— 分配那一步已经扣过了");

        assertEquals(OutboundStatus.SHIPPED.name(), outboundOrderService.get(orderId).getStatus());

        // 必须留下 OUTBOUND_OUT 流水
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE tx_type = 'OUTBOUND_OUT' AND batch_no = ?",
                Integer.class, BATCH_A);
        assertEquals(1, txCount, "发货必须写库存流水（BR-008）");
    }

    /**
     * ★ 回归用例：发货必须减「库位已用容量」。
     *
     * ========== 这条用例是怎么来的 ==========
     * 这个 bug 是先在真实数据里发现的：库位 A-01-01-02 的 used_capacity 是 90，
     * 而它上面实际只有 45 件货。原因就是发货只减了 inventory.qty，
     * 没减 location.used_capacity —— 占用率只增不减，越用越离谱。
     *
     * 修完之后补上这条用例，把「发货 → 占用要减」钉死：
     * 库位占用是个派生计数（上架 +、发货/移出/盘亏 −），
     * 任何「减少库存」的动作忘了同步它，都会在这里变红。
     */
    @Test
    @DisplayName("★ 发货要减库位已用容量（回归：曾经只减库存没减占用）")
    void shipShouldDecreaseUsedCapacity() {
        stockIn(LOCATION_A, BATCH_A, 100);
        // stockIn 是直接调库存内核的，不经过上架任务，所以这里手工把「上架时加的占用」补上，
        // 让用例的起点是真实的：库位上有 100 件货，占用也记着 100。
        jdbcTemplate.update("UPDATE location SET used_capacity = used_capacity + ? WHERE id = ?",
                100, LOCATION_A);
        int before = usedCapacity(LOCATION_A);

        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);
        pickAll(orderId);
        outboundOrderService.ship(orderId);

        assertEquals(before - 40, usedCapacity(LOCATION_A),
                "发货把 40 件真的搬出去了，库位占用必须减 40 —— "
                        + "只减库存不减占用的话，看板的库位占用率会一路虚高，"
                        + "最后空库位也会报「容量不足」");
    }

    @Test
    @DisplayName("★ 短拣必须被拒绝：未实际拣出的数量不能进入发货核销")
    void shortPickShouldBeRejected() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);
        stockTaskService.generatePickTasks(orderId);

        StockTaskQuery query = new StockTaskQuery();
        query.setSourceNo(outboundOrderService.get(orderId).getOrderNo());
        query.setTaskType("PICK");
        StockTaskVO task = stockTaskService.page(query).getRecords().get(0);
        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());

        StockTaskCompleteDTO dto = new StockTaskCompleteDTO();
        dto.setActualQty(task.getPlanQty() - 1);
        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.complete(task.getId(), dto));

        assertEquals(TaskErrorCode.PICK_QTY_MISMATCH.getCode(), ex.getErrorCode().getCode());
        assertEquals(OutboundStatus.PICKING.name(), outboundOrderService.get(orderId).getStatus(),
                "短拣后单据必须停在 PICKING，不能推进到 PICKED");
        assertEquals(40, inventoryOf(LOCATION_A, BATCH_A)[1], "锁定库存不能被错误核销");
    }

    @Test
    @DisplayName("没拣完不能发货")
    void cannotShipBeforePicked() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        BizException ex = assertThrows(BizException.class, () -> outboundOrderService.ship(orderId));
        assertEquals(OutboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("★ 拣货完成不碰库存（三步模型的关键）")
    void pickShouldNotTouchInventory() {
        stockIn(LOCATION_A, BATCH_A, 100);
        Long orderId = createOrder(40);
        outboundOrderService.allocate(orderId);

        int[] afterAllocate = inventoryOf(LOCATION_A, BATCH_A);

        // 走完整的拣货流程（生成任务 → 领取 → 开始 → 完成）
        pickAll(orderId);
        assertEquals(OutboundStatus.PICKED.name(), outboundOrderService.get(orderId).getStatus());

        int[] afterPick = inventoryOf(LOCATION_A, BATCH_A);
        assertEquals(afterAllocate[0], afterPick[0], "拣货后 qty 不能变");
        assertEquals(afterAllocate[1], afterPick[1], "拣货后 locked_qty 不能变");
        assertEquals(afterAllocate[2], afterPick[2], "拣货后可用量不能变");

        // 也不该有 OUTBOUND_OUT 流水（那是发货才写的）
        Integer outTx = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE tx_type = 'OUTBOUND_OUT' AND batch_no = ?",
                Integer.class, BATCH_A);
        assertEquals(0, outTx, "拣货阶段不应该写发货流水");
    }
}
