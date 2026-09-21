package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.dto.StocktakingCountDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingCountLineDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderItemDTO;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.inventory.service.StocktakingOrderService;
import com.myproject.wmsserver.inventory.service.TransferOrderService;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderItemVO;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderVO;
import com.myproject.wmsserver.inventory.vo.TransferOrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 盘点单 / 移库单验收测试。 */
@SpringBootTest
class InventoryOrderFlowTest {

    private static final String WH_CODE = "T-DOC-WH";
    private static final String ZONE_CODE = "T-DOC-ZONE";
    private static final String SKU_CODE = "T-DOC-SKU";
    private static final String BATCH_A = "DOC-A";
    private static final String BATCH_B = "DOC-B";

    @Autowired
    private StocktakingOrderService stocktakingOrderService;

    @Autowired
    private TransferOrderService transferOrderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long warehouseId;
    private Long locationA;
    private Long locationB;
    private Long skuId;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbcTemplate.update("INSERT INTO warehouse (code, name, address, status) VALUES (?, ?, '', 'ENABLED')",
                WH_CODE, "单据测试仓");
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouse WHERE code = ?", Long.class, WH_CODE);
        jdbcTemplate.update("INSERT INTO zone (warehouse_id, code, name, zone_type) VALUES (?, ?, ?, 'STORAGE')",
                warehouseId, ZONE_CODE, "单据测试区");
        Long zoneId = jdbcTemplate.queryForObject(
                "SELECT id FROM zone WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, ZONE_CODE);
        locationA = createLocation(zoneId, "T-DOC-A", 1000);
        locationB = createLocation(zoneId, "T-DOC-B", 1000);
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, status) VALUES (?, ?, '件', 'ENABLED')",
                SKU_CODE, "单据测试商品");
        skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, SKU_CODE);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("盘点单：建单快照、分批录入、批量过账并写盘点来源流水")
    void stocktakingOrderShouldSnapshotAndPost() {
        inbound(locationA, BATCH_A, 100);
        inbound(locationA, BATCH_B, 20);
        seedUsedCapacity(locationA, 120);

        StocktakingOrderCreateDTO create = new StocktakingOrderCreateDTO();
        create.setLocationId(locationA);
        create.setRemark("月度盘点");
        Long orderId = stocktakingOrderService.create(create);

        StocktakingOrderVO created = stocktakingOrderService.get(orderId);
        assertEquals("COUNTING", created.getStatus());
        assertEquals(2, created.getItems().size());

        StocktakingOrderItemVO lineA = created.getItems().stream()
                .filter(item -> BATCH_A.equals(item.getBatchNo())).findFirst().orElseThrow();
        StocktakingOrderItemVO lineB = created.getItems().stream()
                .filter(item -> BATCH_B.equals(item.getBatchNo())).findFirst().orElseThrow();
        stocktakingOrderService.saveCounts(orderId, countDto(
                countLine(lineA.getId(), 90, "少 10"),
                countLine(lineB.getId(), 25, "多 5")));
        stocktakingOrderService.post(orderId);

        StocktakingOrderVO posted = stocktakingOrderService.get(orderId);
        assertEquals("COMPLETED", posted.getStatus());
        assertEquals(2, posted.getCountedItems());
        assertEquals(2, posted.getDiffItems());
        assertEquals(5, posted.getGainQty());
        assertEquals(10, posted.getLossQty());
        assertEquals(90, qty(locationA, BATCH_A));
        assertEquals(25, qty(locationA, BATCH_B));
        assertEquals(115, usedCapacity(locationA));
        assertEquals(1, txCount("STOCKTAKING_GAIN", orderId));
        assertEquals(1, txCount("STOCKTAKING_LOSS", orderId));
    }

    @Test
    @DisplayName("盘点单：还有明细未录入实盘数量时禁止过账")
    void stocktakingOrderShouldRejectIncompletePost() {
        inbound(locationA, BATCH_A, 10);
        StocktakingOrderCreateDTO create = new StocktakingOrderCreateDTO();
        create.setLocationId(locationA);
        Long orderId = stocktakingOrderService.create(create);

        BizException ex = assertThrows(BizException.class,
                () -> stocktakingOrderService.post(orderId));

        assertEquals(InventoryErrorCode.STOCKTAKING_ITEM_INCOMPLETE.getCode(),
                ex.getErrorCode().getCode());
        assertEquals(10, qty(locationA, BATCH_A));
    }

    @Test
    @DisplayName("移库单：多明细一次过账，源减目标加且流水带单据来源")
    void transferOrderShouldMoveAllItemsAtomically() {
        inbound(locationA, BATCH_A, 30);
        inbound(locationA, BATCH_B, 40);
        seedUsedCapacity(locationA, 70);

        Long orderId = transferOrderService.create(transferDto(
                item(skuId, BATCH_A, 10),
                item(skuId, BATCH_B, 15)));
        transferOrderService.complete(orderId);

        TransferOrderVO order = transferOrderService.get(orderId);
        assertEquals("COMPLETED", order.getStatus());
        assertEquals(2, order.getItems().size());
        assertEquals(20, qty(locationA, BATCH_A));
        assertEquals(25, qty(locationA, BATCH_B));
        assertEquals(10, qty(locationB, BATCH_A));
        assertEquals(15, qty(locationB, BATCH_B));
        assertEquals(45, usedCapacity(locationA));
        assertEquals(25, usedCapacity(locationB));
        assertEquals(2, txCount("TRANSFER_OUT", orderId));
        assertEquals(2, txCount("TRANSFER_IN", orderId));
    }

    @Test
    @DisplayName("移库单：第二条库存不足时，第一条转出必须整体回滚")
    void transferOrderShouldRollbackWhenAnyItemFails() {
        inbound(locationA, BATCH_A, 30);
        inbound(locationA, BATCH_B, 20);
        seedUsedCapacity(locationA, 50);

        Long orderId = transferOrderService.create(transferDto(
                item(skuId, BATCH_A, 10),
                item(skuId, BATCH_B, 20)));

        // 建单后第二行库存被别的业务扣掉，制造过账时的并发/库存不足。
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId)
                .locationId(locationA)
                .skuId(skuId)
                .batchNo(BATCH_B)
                .qtyChange(-20)
                .txType(InventoryTxType.STOCKTAKING_LOSS)
                .sourceType("MANUAL")
                .remark("测试制造库存不足")
                .createIfAbsent(false)
                .build());

        assertThrows(BizException.class, () -> transferOrderService.complete(orderId));

        assertEquals(30, qty(locationA, BATCH_A), "第一条转出不能留下部分结果");
        assertEquals(0, qty(locationA, BATCH_B));
        assertEquals(0, qty(locationB, BATCH_A));
        assertEquals(0, txCount("TRANSFER_OUT", orderId));
        assertEquals(0, txCount("TRANSFER_IN", orderId));
    }

    private Long createLocation(Long zoneId, String code, int capacity) {
        jdbcTemplate.update("INSERT INTO location "
                        + "(warehouse_id, zone_id, code, name, location_type, capacity, used_capacity, status) "
                        + "VALUES (?, ?, ?, ?, 'RACK', ?, 0, 'ENABLED')",
                warehouseId, zoneId, code, "单据测试库位" + code, capacity);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM location WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, code);
    }

    private void inbound(Long locationId, String batchNo, int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId)
                .locationId(locationId)
                .skuId(skuId)
                .batchNo(batchNo)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .remark("测试备货")
                .build());
    }

    private void seedUsedCapacity(Long locationId, int qty) {
        jdbcTemplate.update("UPDATE location SET used_capacity = ? WHERE id = ?", qty, locationId);
    }

    private TransferOrderCreateDTO transferDto(TransferOrderItemDTO... items) {
        TransferOrderCreateDTO dto = new TransferOrderCreateDTO();
        dto.setFromLocationId(locationA);
        dto.setToLocationId(locationB);
        dto.setRemark("单据测试移库");
        dto.setItems(List.of(items));
        return dto;
    }

    private TransferOrderItemDTO item(Long skuId, String batchNo, int qty) {
        TransferOrderItemDTO item = new TransferOrderItemDTO();
        item.setSkuId(skuId);
        item.setBatchNo(batchNo);
        item.setQty(qty);
        return item;
    }

    private StocktakingCountDTO countDto(StocktakingCountLineDTO... lines) {
        StocktakingCountDTO dto = new StocktakingCountDTO();
        dto.setItems(List.of(lines));
        return dto;
    }

    private StocktakingCountLineDTO countLine(Long id, int actualQty, String remark) {
        StocktakingCountLineDTO line = new StocktakingCountLineDTO();
        line.setId(id);
        line.setActualQty(actualQty);
        line.setRemark(remark);
        return line;
    }

    private int qty(Long locationId, String batchNo) {
        List<Integer> values = jdbcTemplate.queryForList(
                "SELECT qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, batchNo);
        return values.isEmpty() ? 0 : values.get(0);
    }

    private int usedCapacity(Long locationId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT used_capacity FROM location WHERE id = ?", Integer.class, locationId);
        return value == null ? 0 : value;
    }

    private int txCount(String txType, Long sourceId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE tx_type = ? AND source_id = ?",
                Integer.class, txType, sourceId);
        return value == null ? 0 : value;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stocktaking_order_item WHERE order_id IN "
                + "(SELECT id FROM stocktaking_order WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?))", WH_CODE);
        jdbcTemplate.update("DELETE FROM stocktaking_order WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM transfer_order_item WHERE order_id IN "
                + "(SELECT id FROM transfer_order WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?))", WH_CODE);
        jdbcTemplate.update("DELETE FROM transfer_order WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM location WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM zone WHERE warehouse_id IN "
                + "(SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU_CODE);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code = ?", WH_CODE);
    }
}