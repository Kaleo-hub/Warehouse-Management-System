package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inbound.dto.InboundItemDTO;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.dto.ReceiveItemDTO;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存查询 / 流水查询的验收测试。
 *
 * 测试思路：先把整条入库链路跑通，产生真实的库存和流水，再验证查询接口能不能查出来、
 * 冗余字段（仓库名 / 库位编码 / 商品编码名称）是否都填上了。
 */
@SpringBootTest
@Transactional
class InventoryQueryServiceTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long SKU_ID = 1L;
    private static final String BATCH_NO = "B-QUERY-TEST";

    @Autowired
    private InboundOrderService inboundOrderService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private InventoryQueryService inventoryQueryService;

    /** 跑完整条链路，把货真正上架入库 */
    private StockTaskVO runFullInboundFlow(int qty) {
        InboundOrderSaveDTO save = new InboundOrderSaveDTO();
        save.setWarehouseId(WAREHOUSE_ID);
        save.setSupplierName("查询测试供应商");
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

        stockTaskService.generatePutawayTasks(orderId);

        StockTaskQuery taskQuery = new StockTaskQuery();
        taskQuery.setSourceNo(order.getOrderNo());
        StockTaskVO task = stockTaskService.page(taskQuery).getRecords().get(0);

        stockTaskService.assign(task.getId());
        stockTaskService.start(task.getId());
        StockTaskCompleteDTO done = new StockTaskCompleteDTO();
        done.setActualQty(qty);
        stockTaskService.complete(task.getId(), done);

        return task;
    }

    private InventoryQuery queryByBatch() {
        InventoryQuery query = new InventoryQuery();
        query.setBatchNo(BATCH_NO);
        return query;
    }

    private TransactionQuery txQueryBySource(String sourceNo) {
        TransactionQuery query = new TransactionQuery();
        query.setSourceNo(sourceNo);
        return query;
    }

    @Test
    @DisplayName("库存查询能查到刚上架的货，且冗余字段齐全")
    void inventoryQueryShouldReturnEnrichedFields() {
        StockTaskVO task = runFullInboundFlow(120);

        PageResult<InventoryVO> page = inventoryQueryService.pageInventory(queryByBatch());
        assertEquals(1, page.getTotal(), "这个批次应该恰好有一条库存");

        InventoryVO vo = page.getRecords().get(0);
        assertEquals(120, vo.getQty());
        assertEquals(120, vo.getAvailableQty(), "没有锁定和冻结，可用 = 在库");
        assertEquals(0, vo.getLockedQty());
        assertEquals(0, vo.getFrozenQty());

        // 冗余字段必须都填上 —— 前端要靠它们直接显示，不能只给 id
        assertNotNull(vo.getWarehouseName(), "要有仓库名");
        assertNotNull(vo.getLocationCode(), "要有库位编码");
        assertNotNull(vo.getSkuCode(), "要有 SKU 编码");
        assertNotNull(vo.getSkuName(), "要有商品名称");
        assertNotNull(vo.getInboundTime(), "要有首次入库时间（FIFO 要用）");

        assertEquals(task.getToLocationId(), vo.getLocationId());
        assertEquals(task.getSkuId(), vo.getSkuId());
    }

    @Test
    @DisplayName("流水查询能查到刚写入的上架入库流水，且变动前后正确")
    void transactionQueryShouldReturnPutawayRecord() {
        StockTaskVO task = runFullInboundFlow(80);

        PageResult<InventoryTransactionVO> page =
                inventoryQueryService.pageTransaction(txQueryBySource(task.getTaskNo()));
        assertEquals(1, page.getTotal(), "上架任务应该只产生一条流水");

        InventoryTransactionVO vo = page.getRecords().get(0);
        assertEquals("PUTAWAY_IN", vo.getTxType());
        assertEquals(80, vo.getQtyChange());
        assertEquals(0, vo.getQtyBefore(), "第一次入库，变动前是 0");
        assertEquals(80, vo.getQtyAfter());
        assertEquals(task.getTaskNo(), vo.getSourceNo());
        assertNotNull(vo.getTxNo());
        assertNotNull(vo.getWarehouseName());
        assertNotNull(vo.getLocationCode());
        assertNotNull(vo.getSkuCode());
        assertNotNull(vo.getOperatorName(), "要记录操作人");
    }

    @Test
    @DisplayName("按流水类型过滤：只看上架入库")
    void filterByTxType() {
        runFullInboundFlow(30);

        TransactionQuery query = new TransactionQuery();
        query.setTxType("PUTAWAY_IN");
        query.setSkuId(SKU_ID);
        PageResult<InventoryTransactionVO> page = inventoryQueryService.pageTransaction(query);

        assertTrue(page.getTotal() >= 1);
        page.getRecords().forEach(vo -> assertEquals("PUTAWAY_IN", vo.getTxType()));
    }

    @Test
    @DisplayName("按 SKU 过滤库存")
    void filterInventoryBySku() {
        runFullInboundFlow(25);

        InventoryQuery query = new InventoryQuery();
        query.setSkuId(SKU_ID);
        query.setWarehouseId(WAREHOUSE_ID);
        PageResult<InventoryVO> page = inventoryQueryService.pageInventory(query);

        assertTrue(page.getTotal() >= 1);
        page.getRecords().forEach(vo -> assertEquals(SKU_ID, vo.getSkuId()));
    }

    @Test
    @DisplayName("查不存在的批次返回空列表，不报错")
    void emptyResultShouldNotThrow() {
        InventoryQuery query = new InventoryQuery();
        query.setBatchNo("NOT-EXIST-BATCH-XXX");
        PageResult<InventoryVO> page = inventoryQueryService.pageInventory(query);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("库存的 availableQty 始终等于 qty - lockedQty - frozenQty（BR-002）")
    void availableQtyShouldAlwaysBeConsistent() {
        runFullInboundFlow(60);

        PageResult<InventoryVO> page = inventoryQueryService.pageInventory(queryByBatch());
        assertFalse(page.getRecords().isEmpty());

        page.getRecords().forEach(vo -> assertEquals(
                vo.getQty() - vo.getLockedQty() - vo.getFrozenQty(),
                vo.getAvailableQty(),
                "可用数量必须等于 在库 - 锁定 - 冻结"));
    }
}