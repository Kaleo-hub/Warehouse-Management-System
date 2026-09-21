package com.myproject.wmsserver.outbound;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.outbound.dto.OutboundItemDTO;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 出库红冲验收测试（BR-011）。
 *
 * 测试必须走完整发货链路，不能直接改状态伪造 SHIPPED：
 * 红冲依赖原单的 PICKED 分配记录和已核销库存，直接改状态会绕掉真正的业务前提。
 */
@SpringBootTest
@Transactional
class OutboundReverseTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID = 3L;
    private static final String BATCH_NO = "OUT-REVERSE-TEST";
    private static final String TEST_SKU_CODE = "TEST-OUT-REVERSE-SKU";

    private Long testSkuId;

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockTaskService stockTaskService;

    @Autowired
    private LocationService locationService;

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
                + "VALUES (?, '红冲测试商品', '件', 365, 'ENABLED')", TEST_SKU_CODE);
        testSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, TEST_SKU_CODE);
    }

    @AfterEach
    void dropTestSku() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id = ?", testSkuId);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", TEST_SKU_CODE);
    }

    @Test
    @DisplayName("★ BR-011：红冲建立反向单、恢复库存并保留完整凭证链")
    void reverseShouldRestoreInventoryAndKeepAuditChain() {
        int usedBefore = usedCapacity();
        Long originalId = shipOneOrder(100, 40);

        assertEquals(usedBefore + 60, usedCapacity(), "发货后库位占用应减少 40");

        Long reverseId = outboundOrderService.reverse(originalId);

        String originalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_order WHERE id = ?", String.class, originalId);
        Long reversedBy = jdbcTemplate.queryForObject(
                "SELECT reversed_by_id FROM outbound_order WHERE id = ?", Long.class, originalId);
        assertEquals(OutboundStatus.REVERSED.name(), originalStatus);
        assertEquals(reverseId, reversedBy);

        Long reverseOf = jdbcTemplate.queryForObject(
                "SELECT reverse_of_id FROM outbound_order WHERE id = ?", Long.class, reverseId);
        String reverseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_order WHERE id = ?", String.class, reverseId);
        Integer reverseTotal = jdbcTemplate.queryForObject(
                "SELECT total_qty FROM outbound_order WHERE id = ?", Integer.class, reverseId);
        assertEquals(originalId, reverseOf, "红冲单必须指回原单");
        assertEquals(OutboundStatus.SHIPPED.name(), reverseStatus, "红冲单本身是已完成的反向凭证");
        assertEquals(40, reverseTotal);

        Integer reverseItemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbound_order_item WHERE order_id = ? AND deleted = 0",
                Integer.class, reverseId);
        Integer reverseItemQty = jdbcTemplate.queryForObject(
                "SELECT qty FROM outbound_order_item WHERE order_id = ? AND deleted = 0",
                Integer.class, reverseId);
        assertEquals(1, reverseItemCount, "红冲单必须复制原单明细");
        assertEquals(40, reverseItemQty);

        assertArrayEquals(new int[]{100, 0, 100}, inventory(), "红冲后库存、锁定和可用量都应恢复到发货前");
        assertEquals(usedBefore + 100, usedCapacity(), "红冲退回实物后库位占用也必须恢复");

        Integer reverseTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction "
                        + "WHERE tx_type = 'OUTBOUND_REVERSE' AND source_id = ?",
                Integer.class, reverseId);
        Integer reverseTxQty = jdbcTemplate.queryForObject(
                "SELECT qty_change FROM inventory_transaction "
                        + "WHERE tx_type = 'OUTBOUND_REVERSE' AND source_id = ?",
                Integer.class, reverseId);
        assertEquals(1, reverseTxCount, "红冲必须恰好写一条反向流水");
        assertEquals(40, reverseTxQty, "反向流水数量必须等于退回库存数量");
    }

    @Test
    @DisplayName("同一张已发货单只能红冲一次，二次调用不能重复加库存")
    void secondReverseShouldBeRejectedWithoutChangingInventory() {
        Long originalId = shipOneOrder(100, 40);
        outboundOrderService.reverse(originalId);
        int[] afterFirstReverse = inventory();

        BizException ex = assertThrows(BizException.class,
                () -> outboundOrderService.reverse(originalId));

        assertEquals(OutboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
        assertArrayEquals(afterFirstReverse, inventory(), "二次红冲失败后库存不能再次增加");
        Integer reverseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbound_order WHERE reverse_of_id = ?",
                Integer.class, originalId);
        assertEquals(1, reverseCount, "一张原单只能存在一张红冲单");
    }

    @Test
    @DisplayName("未发货单据不能红冲")
    void cannotReverseBeforeShipped() {
        stockIn(100);
        Long originalId = createOrder(40);
        int[] before = inventory();

        BizException ex = assertThrows(BizException.class,
                () -> outboundOrderService.reverse(originalId));

        assertEquals(OutboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
        assertArrayEquals(before, inventory(), "失败的红冲不能改库存");
    }

    @Test
    @DisplayName("已红冲单据按终态排序，不能继续排在待处理区")
    void reversedOrderShouldBeSortedAsTerminal() {
        String customer = "红冲排序测试-" + System.nanoTime();
        Long openOrderId = createOrder(10, customer);
        Long shippedOrderId = shipOneOrder(100, 10, customer);
        outboundOrderService.reverse(shippedOrderId);

        OutboundOrderQuery query = new OutboundOrderQuery();
        query.setPage(1);
        query.setSize(20);
        query.setKeyword(customer);
        List<OutboundOrderVO> records = outboundOrderService.page(query).getRecords();

        assertEquals(openOrderId, records.get(0).getId(),
                "CREATED 单据必须排在 REVERSED 终态单据之前");
    }

    private Long shipOneOrder(int stockQty, int orderQty) {
        return shipOneOrder(stockQty, orderQty, "红冲测试客户");
    }

    private Long shipOneOrder(int stockQty, int orderQty, String customer) {
        stockIn(stockQty);
        Long orderId = createOrder(orderQty, customer);
        outboundOrderService.allocate(orderId);
        pickAll(orderId);
        outboundOrderService.ship(orderId);
        assertEquals(OutboundStatus.SHIPPED.name(), orderStatus(orderId));
        return orderId;
    }

    private void stockIn(int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(WAREHOUSE_ID)
                .locationId(LOCATION_ID)
                .skuId(testSkuId)
                .batchNo(BATCH_NO)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("红冲测试备货")
                .build());
        locationService.increaseUsedCapacity(LOCATION_ID, qty);
    }

    private Long createOrder(int qty) {
        return createOrder(qty, "红冲测试客户");
    }

    private Long createOrder(int qty, String customer) {
        OutboundOrderSaveDTO dto = new OutboundOrderSaveDTO();
        dto.setWarehouseId(WAREHOUSE_ID);
        dto.setCustomerName(customer);
        dto.setReceiveAddress("上海市测试路 1 号");

        OutboundItemDTO item = new OutboundItemDTO();
        item.setSkuId(testSkuId);
        item.setQty(qty);
        dto.setItems(List.of(item));

        return outboundOrderService.create(dto);
    }

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

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbound_order WHERE id = ?", String.class, orderId);
    }

    private int[] inventory() {
        return jdbcTemplate.queryForObject(
                "SELECT qty, locked_qty, available_qty FROM inventory "
                        + "WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                (rs, rowNum) -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)},
                LOCATION_ID, testSkuId, BATCH_NO);
    }

    private int usedCapacity() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT used_capacity FROM location WHERE id = ?", Integer.class, LOCATION_ID);
        return value == null ? 0 : value;
    }
}
