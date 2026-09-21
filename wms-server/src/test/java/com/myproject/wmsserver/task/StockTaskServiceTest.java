package com.myproject.wmsserver.task;

import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InboundErrorCode;
import com.myproject.wmsserver.common.exception.TaskErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inbound.dto.InboundItemDTO;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.dto.ReceiveItemDTO;
import com.myproject.wmsserver.inbound.enums.InboundStatus;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.enums.TaskStatus;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
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
 * 第 ③ 步验收测试：生成上架任务 → 领取 → 开始 → 完成上架 → 库存变化。
 *
 * 这是整个入库链路收口的地方：① 库存内核 和 ② 入库单 在这里被串起来。
 */
@SpringBootTest
@Transactional
class StockTaskServiceTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long SKU_ID = 1L;
    private static final String BATCH_NO = "B-STOCKTASK-TEST";

    @Autowired
    private InboundOrderService inboundOrderService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* ---------------------------- 工具 ---------------------------- */

    /** 建一张「已收货」的入库单，返回单据 VO */
    private InboundOrderVO prepareReceivedOrder(int qty) {
        InboundOrderSaveDTO save = new InboundOrderSaveDTO();
        save.setWarehouseId(WAREHOUSE_ID);
        save.setSupplierName("任务测试供应商");
        InboundItemDTO item = new InboundItemDTO();
        item.setSkuId(SKU_ID);
        item.setExpectQty(qty);
        save.setItems(List.of(item));

        Long orderId = inboundOrderService.create(save);
        InboundOrderVO order = inboundOrderService.get(orderId);

        ReceiveItemDTO recvItem = new ReceiveItemDTO();
        recvItem.setItemId(order.getItems().get(0).getId());
        recvItem.setReceivedQty(qty);
        recvItem.setBatchNo(BATCH_NO);
        InboundReceiveDTO recv = new InboundReceiveDTO();
        recv.setItems(List.of(recvItem));
        inboundOrderService.receive(orderId, recv);

        return inboundOrderService.get(orderId);
    }

    private StockTaskVO firstTaskOf(String orderNo) {
        StockTaskQuery query = new StockTaskQuery();
        query.setSourceNo(orderNo);
        PageResult<StockTaskVO> page = stockTaskService.page(query);
        assertTrue(!page.getRecords().isEmpty(), "应该已经生成了上架任务");
        return page.getRecords().get(0);
    }

    private int inventoryQty(Long locationId, Long skuId, String batchNo) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, batchNo);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int transactionCount(String sourceNo) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE source_no = ?", Integer.class, sourceNo);
        return n == null ? 0 : n;
    }

    private StockTaskCompleteDTO completeDto(int qty) {
        StockTaskCompleteDTO dto = new StockTaskCompleteDTO();
        dto.setActualQty(qty);
        return dto;
    }

    /* ---------------------------- 生成任务 ---------------------------- */

    @Test
    @DisplayName("生成上架任务：按已收货数量生成，并推荐目标库位")
    void generatePutawayTasks() {
        InboundOrderVO order = prepareReceivedOrder(100);

        int created = stockTaskService.generatePutawayTasks(order.getId());
        assertEquals(1, created);

        StockTaskVO task = firstTaskOf(order.getOrderNo());
        assertEquals("PUTAWAY", task.getTaskType());
        assertEquals(TaskStatus.CREATED.name(), task.getStatus());
        assertEquals(100, task.getPlanQty());
        assertEquals(0, task.getActualQty());
        assertTrue(task.getTaskNo().startsWith("TASK"), "任务号应以 TASK 开头");
        assertNotNull(task.getToLocationCode(), "应该推荐出目标库位");

        // 单据状态保持 RECEIVED —— 要等所有任务完成才变 PUTAWAY_DONE
        assertEquals(InboundStatus.RECEIVED.name(), inboundOrderService.get(order.getId()).getStatus());
    }

    @Test
    @DisplayName("重复生成上架任务 → 10007（幂等）")
    void generateTwiceShouldBeIdempotent() {
        InboundOrderVO order = prepareReceivedOrder(100);
        stockTaskService.generatePutawayTasks(order.getId());

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.generatePutawayTasks(order.getId()));
        assertEquals(CommonErrorCode.IDEMPOTENT_CONFLICT.getCode(), ex.getErrorCode().getCode());

        // 不能真的多生成一条
        StockTaskQuery query = new StockTaskQuery();
        query.setSourceNo(order.getOrderNo());
        assertEquals(1, stockTaskService.page(query).getTotal());
    }

    @Test
    @DisplayName("还没收货就生成上架任务 → 40002")
    void cannotGenerateBeforeReceived() {
        InboundOrderSaveDTO save = new InboundOrderSaveDTO();
        save.setWarehouseId(WAREHOUSE_ID);
        InboundItemDTO item = new InboundItemDTO();
        item.setSkuId(SKU_ID);
        item.setExpectQty(10);
        save.setItems(List.of(item));
        Long orderId = inboundOrderService.create(save);

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.generatePutawayTasks(orderId));
        assertEquals(InboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 状态机 ---------------------------- */

    @Test
    @DisplayName("任务状态机：不能跳过领取直接开始")
    void cannotStartBeforeAssign() {
        InboundOrderVO order = prepareReceivedOrder(50);
        stockTaskService.generatePutawayTasks(order.getId());
        Long taskId = firstTaskOf(order.getOrderNo()).getId();

        BizException ex = assertThrows(BizException.class, () -> stockTaskService.start(taskId));
        assertEquals(TaskErrorCode.TASK_STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("任务状态机：不能跳过开始直接完成")
    void cannotCompleteBeforeStart() {
        InboundOrderVO order = prepareReceivedOrder(50);
        stockTaskService.generatePutawayTasks(order.getId());
        Long taskId = firstTaskOf(order.getOrderNo()).getId();
        stockTaskService.assign(taskId);

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.complete(taskId, completeDto(50)));
        assertEquals(TaskErrorCode.TASK_STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 完成上架 ★ ---------------------------- */

    @Test
    @DisplayName("★ 完成上架 → 库存增加 + 写流水 + 单据变 PUTAWAY_DONE")
    void completePutawayShouldIncreaseInventory() {
        InboundOrderVO order = prepareReceivedOrder(100);
        stockTaskService.generatePutawayTasks(order.getId());
        StockTaskVO task = firstTaskOf(order.getOrderNo());

        int before = inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO);
        assertEquals(0, before, "上架前这个库位不该有这批货");

        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());
        stockTaskService.complete(task.getId(), completeDto(100));

        // ① 库存真的增加了
        assertEquals(100, inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO),
                "上架完成后库存应该增加 100");

        // ② 写了一条 PUTAWAY_IN 流水
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE source_no = ? AND tx_type = 'PUTAWAY_IN'",
                Integer.class, task.getTaskNo());
        assertEquals(1, txCount, "必须写一条上架入库流水（BR-008）");

        // ③ 流水记了变动前后
        Integer qtyBefore = jdbcTemplate.queryForObject(
                "SELECT qty_before FROM inventory_transaction WHERE source_no = ?", Integer.class, task.getTaskNo());
        Integer qtyAfter = jdbcTemplate.queryForObject(
                "SELECT qty_after FROM inventory_transaction WHERE source_no = ?", Integer.class, task.getTaskNo());
        assertEquals(0, qtyBefore);
        assertEquals(100, qtyAfter);

        // ④ 任务完成
        assertEquals(TaskStatus.DONE.name(), stockTaskService.get(task.getId()).getStatus());

        // ⑤ 单据所有明细都上架完 → PUTAWAY_DONE
        assertEquals(InboundStatus.PUTAWAY_DONE.name(), inboundOrderService.get(order.getId()).getStatus());
        assertEquals(100, inboundOrderService.get(order.getId()).getItems().get(0).getPutawayQty());
    }

    @Test
    @DisplayName("全部上架完后，任务不能再被完成（防重复加库存）")
    void cannotCompleteTwice() {
        InboundOrderVO order = prepareReceivedOrder(30);
        stockTaskService.generatePutawayTasks(order.getId());
        StockTaskVO task = firstTaskOf(order.getOrderNo());
        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());
        stockTaskService.complete(task.getId(), completeDto(30));

        assertEquals(30, inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO));

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.complete(task.getId(), completeDto(30)));
        assertEquals(TaskErrorCode.TASK_STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());

        assertEquals(30, inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO),
                "重复完成绝不能把库存加第二遍");
        assertEquals(1, transactionCount(task.getTaskNo()), "也不能多写流水");
    }

    @Test
    @DisplayName("实际上架数量不能超过计划数量")
    void cannotCompleteMoreThanPlan() {
        InboundOrderVO order = prepareReceivedOrder(20);
        stockTaskService.generatePutawayTasks(order.getId());
        StockTaskVO task = firstTaskOf(order.getOrderNo());
        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.complete(task.getId(), completeDto(21)));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), ex.getErrorCode().getCode());

        assertEquals(0, inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO),
                "校验失败不能改库存");
    }

    @Test
    @DisplayName("少收少上：实际上架数量小于计划时，库存按实际数量增加")
    void partialPutaway() {
        InboundOrderVO order = prepareReceivedOrder(100);
        stockTaskService.generatePutawayTasks(order.getId());
        StockTaskVO task = firstTaskOf(order.getOrderNo());
        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());

        // 作业过程中破损了 10 件，只上架 90
        stockTaskService.complete(task.getId(), completeDto(90));

        assertEquals(90, inventoryQty(task.getToLocationId(), task.getSkuId(), BATCH_NO),
                "库存应该按实际数量增加，账实一致优先");
        assertEquals(TaskStatus.DONE.name(), stockTaskService.get(task.getId()).getStatus());
        assertEquals(90, stockTaskService.get(task.getId()).getActualQty());
    }
    @Test
    @DisplayName("任务只能由领取人开始/完成，其他作业员不能越权操作")
    void onlyAssigneeCanOperateTask() {
        InboundOrderVO order = prepareReceivedOrder(10);
        stockTaskService.generatePutawayTasks(order.getId());
        StockTaskVO task = firstTaskOf(order.getOrderNo());

        try {
            UserContext.set(new UserContext.CurrentUser(101L, "operator-a", "OPERATOR"));
            stockTaskService.assign(task.getId());
            UserContext.clear();

            UserContext.set(new UserContext.CurrentUser(102L, "operator-b", "OPERATOR"));
            BizException ex = assertThrows(BizException.class, () -> stockTaskService.start(task.getId()));
            assertEquals(AuthErrorCode.FORBIDDEN.getCode(), ex.getErrorCode().getCode());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("没有满足容量和待处理占用的上架库位时，生成任务直接报容量不足")
    void generatePutawayTasksShouldRejectWhenNoCapacity() {
        InboundOrderVO order = prepareReceivedOrder(100);
        jdbcTemplate.update("UPDATE location SET used_capacity = capacity "
                + "WHERE warehouse_id = ? AND location_type = 'RACK' AND capacity > 0", WAREHOUSE_ID);

        BizException ex = assertThrows(BizException.class,
                () -> stockTaskService.generatePutawayTasks(order.getId()));
        assertEquals(CommonErrorCode.LOCATION_CAPACITY_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());
    }

}
