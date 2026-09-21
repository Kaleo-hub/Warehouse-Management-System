package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.dto.SummaryQuery;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存看板验收测试：总览 / 三维度汇总 / 三类预警。
 *
 * 【测试隔离】聚合查询是"全库汇总"，直接断言会被库里已有的真实数据干扰。
 * 所以这里自建一个【专用仓库 + 库区 + 库位 + SKU】，所有断言都在这个仓库范围内做，
 * 用 @AfterEach 物理清理。
 */
@SpringBootTest
@Transactional
class InventorySummaryServiceTest {

    private static final String WH_CODE = "TEST-SUMMARY-WH";
    private static final String ZONE_CODE = "T-Z";
    private static final String LOC_CODE = "T-L-01";
    private static final String SKU_CODE = "TEST-SUMMARY-SKU";
    private static final String BATCH_NO = "T-BATCH-001";
    private static final String PENDING_ORDER_NO = "T-SUMMARY-PENDING";
    private static final int CAPACITY = 1000;

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryQueryService inventoryQueryService;
    @Autowired private LocationService locationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long warehouseId;
    private Long locationId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        // 先清掉可能残留的同名数据（逻辑删除不影响唯一键，所以只能物理删）
        jdbcTemplate.update("DELETE FROM inventory WHERE batch_no = ?", BATCH_NO);
        jdbcTemplate.update("DELETE FROM inbound_order WHERE order_no = ?", PENDING_ORDER_NO);
        jdbcTemplate.update("DELETE FROM location WHERE code = ?", LOC_CODE);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code = ?", WH_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU_CODE);

        jdbcTemplate.update("INSERT INTO warehouse (code, name, status) VALUES (?, '测试汇总仓', 'ENABLED')", WH_CODE);
        warehouseId = jdbcTemplate.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, WH_CODE);

        jdbcTemplate.update("INSERT INTO zone (warehouse_id, code, name, zone_type, sort_no, status) "
                + "VALUES (?, ?, '测试库区', 'STORAGE', 1, 'ENABLED')", warehouseId, ZONE_CODE);
        Long zoneId = jdbcTemplate.queryForObject(
                "SELECT id FROM zone WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, ZONE_CODE);

        jdbcTemplate.update("INSERT INTO location (warehouse_id, zone_id, code, name, location_type, capacity, status) "
                + "VALUES (?, ?, ?, '测试库位', 'RACK', ?, 'ENABLED')", warehouseId, zoneId, LOC_CODE, CAPACITY);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM location WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, LOC_CODE);

        jdbcTemplate.update("INSERT INTO product (sku_code, name, spec, unit, shelf_life_days, status) "
                + "VALUES (?, '测试汇总商品', '1件/件', '件', 365, 'ENABLED')", SKU_CODE);
        skuId = jdbcTemplate.queryForObject("SELECT id FROM product WHERE sku_code = ?", Long.class, SKU_CODE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE batch_no = ?", BATCH_NO);
        jdbcTemplate.update("DELETE FROM inbound_order WHERE order_no = ?", PENDING_ORDER_NO);
        jdbcTemplate.update("DELETE FROM inventory WHERE batch_no = ?", BATCH_NO);
        jdbcTemplate.update("DELETE FROM location WHERE code = ?", LOC_CODE);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code = ?", WH_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU_CODE);
    }

    /** 往测试库位入库；expiryDate 传 null 表示不登记效期 */
    private void stockIn(int qty, LocalDate expiryDate) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId)
                .locationId(locationId)
                .skuId(skuId)
                .batchNo(BATCH_NO)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试")
                .productionDate(expiryDate == null ? null : expiryDate.minusYears(2))
                .expiryDate(expiryDate)
                .build());
        locationService.increaseUsedCapacity(locationId, qty);
    }

    /* ==================== 总览 ==================== */

    @Test
    @DisplayName("总览：在库量、SKU 数、库位数、容量占用率都对得上")
    void summaryShouldAggregateStockAndCapacity() {
        stockIn(120, null);

        InventorySummaryVO vo = inventoryQueryService.summary(warehouseId);

        assertEquals(120L, vo.getTotalQty());
        assertEquals(0L, vo.getLockedQty());
        assertEquals(0L, vo.getFrozenQty());
        assertEquals(120L, vo.getAvailableQty());
        assertEquals(1, vo.getSkuCount(), "只有 1 个 SKU 有货");
        assertEquals(1, vo.getUsedLocationCount(), "只有 1 个库位有货");

        assertEquals(1, vo.getLocationCount(), "启用库位 1 个");
        assertEquals(1000L, vo.getCapacityTotal());
        assertEquals(120L, vo.getCapacityUsed());
        assertEquals(12.0, vo.getCapacityUsageRate(), "120/1000 = 12%");

        // 按仓库分布里也应该有这一个仓库
        assertNotNull(vo.getByWarehouse());
        assertEquals(1, vo.getByWarehouse().size());
        assertEquals(warehouseId, vo.getByWarehouse().get(0).getGroupId());
        assertEquals(120L, vo.getByWarehouse().get(0).getQty());
    }

    @Test
    @DisplayName("总览：锁定和冻结会从可用量里扣掉")
    void summaryShouldReflectLockedAndFrozen() {
        stockIn(100, null);

        // 锁 30（出库分配）+ 冻 20（质检）
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId).locationId(locationId).skuId(skuId).batchNo(BATCH_NO)
                .lockedChange(30).txType(InventoryTxType.ALLOCATE_LOCK)
                .sourceType("MANUAL").operatorName("测试").createIfAbsent(false).build());
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId).locationId(locationId).skuId(skuId).batchNo(BATCH_NO)
                .frozenChange(20).txType(InventoryTxType.FREEZE)
                .sourceType("MANUAL").operatorName("测试").createIfAbsent(false).build());

        InventorySummaryVO vo = inventoryQueryService.summary(warehouseId);

        assertEquals(100L, vo.getTotalQty(), "在库不变");
        assertEquals(30L, vo.getLockedQty());
        assertEquals(20L, vo.getFrozenQty());
        assertEquals(50L, vo.getAvailableQty(), "100 - 30 - 20 = 50");
    }

    /* ==================== 分组汇总 ==================== */

    @Test
    @DisplayName("三维度汇总：仓库 / 库区 / SKU 各返回 1 组，且数量一致")
    void groupedSummaryShouldWorkForAllDimensions() {
        stockIn(80, null);

        assertEquals(1, rowsOf("WAREHOUSE").size());
        assertEquals(1, rowsOf("ZONE").size());
        assertEquals(1, rowsOf("SKU").size());

        for (String dim : new String[]{"WAREHOUSE", "ZONE", "SKU"}) {
            InventoryGroupVO group = rowsOf(dim).get(0);
            assertEquals(80L, group.getQty(), dim + " 维度的在库量应该是 80");
            assertEquals(80L, group.getAvailableQty(), dim + " 维度的可用量应该是 80");
            assertEquals(1, group.getLocationCount(), dim + " 维度应该只有 1 个库位");
        }

        // ZONE 维度会带上所属仓库名
        assertEquals("测试汇总仓", rowsOf("ZONE").get(0).getParentName());
    }

    @Test
    @DisplayName("维度传非法值：退化成默认的按仓库，不报错")
    void groupedSummaryShouldFallbackOnUnknownDimension() {
        stockIn(10, null);
        SummaryQuery query = new SummaryQuery();
        query.setDimension("DROP TABLE inventory");
        query.setWarehouseId(warehouseId);

        List<InventoryGroupVO> list = inventoryQueryService.groupedSummary(query);
        assertEquals(1, list.size(), "非法维度应该退化成默认维度");
        assertEquals(warehouseId, list.get(0).getGroupId());
    }

    private List<InventoryGroupVO> rowsOf(String dimension) {
        SummaryQuery query = new SummaryQuery();
        query.setDimension(dimension);
        query.setWarehouseId(warehouseId);
        return inventoryQueryService.groupedSummary(query);
    }

    /* ==================== 预警 ==================== */

    @Test
    @DisplayName("临期预警：到期日在阈值内会命中，已过期的也会命中")
    void expiringAlert() {
        stockIn(50, LocalDate.now().plusDays(10));   // 10 天后到期

        SummaryQuery query = new SummaryQuery();
        query.setWarehouseId(warehouseId);
        query.setExpireDays(30);
        InventoryAlertVO alerts = inventoryQueryService.alerts(query);

        assertEquals(1, alerts.getExpiring().size());
        InventoryAlertVO.ExpiryAlert alert = alerts.getExpiring().get(0);
        assertEquals(BATCH_NO, alert.getBatchNo());
        assertEquals(50, alert.getQty());
        assertEquals(10, alert.getDaysToExpire(), "距到期还有 10 天");
        assertEquals(30, alerts.getExpireDays());
    }

    @Test
    @DisplayName("临期预警：不在阈值内的不会命中")
    void expiringAlertShouldRespectThreshold() {
        stockIn(50, LocalDate.now().plusDays(200));   // 200 天后到期

        SummaryQuery query = new SummaryQuery();
        query.setWarehouseId(warehouseId);
        query.setExpireDays(30);

        assertTrue(inventoryQueryService.alerts(query).getExpiring().isEmpty(),
                "200 天后到期的不该出现在 30 天临期里");
    }

    @Test
    @DisplayName("临期预警：没登记到期日期的不会命中")
    void expiringAlertShouldSkipNullExpiry() {
        stockIn(50, null);

        SummaryQuery query = new SummaryQuery();
        query.setWarehouseId(warehouseId);
        query.setExpireDays(30);

        assertTrue(inventoryQueryService.alerts(query).getExpiring().isEmpty(),
                "没登记效期的货不该报临期");
    }

    @Test
    @DisplayName("冻结预警：有冻结量的才命中")
    void frozenAlert() {
        stockIn(100, null);
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId).locationId(locationId).skuId(skuId).batchNo(BATCH_NO)
                .frozenChange(25).txType(InventoryTxType.FREEZE)
                .sourceType("MANUAL").operatorName("测试").createIfAbsent(false).build());

        SummaryQuery query = new SummaryQuery();
        query.setWarehouseId(warehouseId);
        InventoryAlertVO alerts = inventoryQueryService.alerts(query);

        assertEquals(1, alerts.getFrozen().size());
        assertEquals(25, alerts.getFrozen().get(0).getFrozenQty());
        assertEquals(75, alerts.getFrozen().get(0).getAvailableQty());
    }

    @Test
    @DisplayName("超容预警：已用过容量上限的库位才命中")
    void overCapacityAlert() {
        stockIn(50, null);
        // 正常流程不会超容，这里直接改库造出异常数据
        jdbcTemplate.update("UPDATE location SET used_capacity = capacity + 50 WHERE id = ?", locationId);

        SummaryQuery query = new SummaryQuery();
        query.setWarehouseId(warehouseId);
        InventoryAlertVO alerts = inventoryQueryService.alerts(query);

        assertEquals(1, alerts.getOverCapacity().size());
        InventoryAlertVO.CapacityAlert alert = alerts.getOverCapacity().get(0);
        assertEquals(locationId, alert.getLocationId());
        assertEquals(CAPACITY, alert.getCapacity());
        assertEquals(CAPACITY + 50, alert.getUsedCapacity());
        assertEquals(50, alert.getOverQty(), "超出 50");
    }

    @Test
    @DisplayName("预警：仓库过滤生效 —— 别的仓库的数据不会串进来")
    void alertsShouldRespectWarehouseFilter() {
        stockIn(50, LocalDate.now().plusDays(5));

        // 不带仓库过滤时，至少能查到这条（可能还有别的真实数据）
        SummaryQuery all = new SummaryQuery();
        assertTrue(inventoryQueryService.alerts(all).getExpiring().size() >= 1);

        // 带别的仓库 id 过滤时，查不到这条
        SummaryQuery other = new SummaryQuery();
        other.setWarehouseId(warehouseId + 999999);
        assertTrue(inventoryQueryService.alerts(other).getExpiring().isEmpty(),
                "过滤到不存在的仓库时不该有数据");
    }
}
