package com.myproject.wmsserver.basedata;

import com.myproject.wmsserver.basedata.dto.LocationSaveDTO;
import com.myproject.wmsserver.basedata.dto.ZoneSaveDTO;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.service.ZoneService;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 基础数据修改和删除时的结构一致性回归。 */
@SpringBootTest
@Transactional
class BaseDataIntegrityTest {

    private static final String WH_A = "T-INTEGRITY-A";
    private static final String WH_B = "T-INTEGRITY-B";
    private static final String ZONE_A = "T-INTEGRITY-ZA";
    private static final String ZONE_B = "T-INTEGRITY-ZB";
    private static final String LOC = "T-INTEGRITY-L";
    private static final String SKU = "T-INTEGRITY-SKU";

    @Autowired
    private LocationService locationService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long warehouseA;
    private Long warehouseB;
    private Long zoneA;
    private Long zoneB;
    private Long locationId;
    private Long skuId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inventory WHERE sku_id IN (SELECT id FROM product WHERE sku_code = ?)", SKU);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU);
        jdbcTemplate.update("DELETE FROM location WHERE code = ?", LOC);
        jdbcTemplate.update("DELETE FROM zone WHERE code IN (?, ?)", ZONE_A, ZONE_B);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code IN (?, ?)", WH_A, WH_B);

        warehouseA = createWarehouse(WH_A, "一致性测试仓 A");
        warehouseB = createWarehouse(WH_B, "一致性测试仓 B");
        zoneA = createZone(warehouseA, ZONE_A, "A 库区");
        zoneB = createZone(warehouseB, ZONE_B, "B 库区");
        jdbcTemplate.update("INSERT INTO location "
                        + "(warehouse_id, zone_id, code, name, location_type, capacity, used_capacity, status) "
                        + "VALUES (?, ?, ?, '一致性测试库位', 'RACK', 100, 50, 'ENABLED')",
                warehouseA, zoneA, LOC);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM location WHERE code = ?", Long.class, LOC);
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, status) VALUES (?, ?, '件', 'ENABLED')",
                SKU, "完整性测试商品");
        skuId = jdbcTemplate.queryForObject("SELECT id FROM product WHERE sku_code = ?", Long.class, SKU);
    }

    @Test
    @DisplayName("库位有货时不能改所属仓库，否则库存和仓库归属会错位")
    void usedLocationCannotMoveWarehouse() {
        LocationSaveDTO dto = locationDto(warehouseB, zoneB, 100);
        BizException ex = assertThrows(BizException.class,
                () -> locationService.update(locationId, dto));
        assertEquals(CommonErrorCode.RELATED_DATA_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("容量不能调到已用容量以下")
    void capacityCannotBeBelowUsedCapacity() {
        LocationSaveDTO dto = locationDto(warehouseA, zoneA, 40);
        BizException ex = assertThrows(BizException.class,
                () -> locationService.update(locationId, dto));
        assertEquals(CommonErrorCode.LOCATION_CAPACITY_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("used_capacity 为 0 但仍有库存时，库位也不能删除")
    void locationWithInventoryCannotBeDeletedEvenWhenCounterIsZero() {
        jdbcTemplate.update("UPDATE location SET used_capacity = 0 WHERE id = ?", locationId);
        jdbcTemplate.update("INSERT INTO inventory (warehouse_id, location_id, sku_id, qty) VALUES (?, ?, ?, 1)",
                warehouseA, locationId, skuId);

        BizException ex = assertThrows(BizException.class, () -> locationService.delete(locationId));
        assertEquals(CommonErrorCode.RELATED_DATA_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("商品还有库存时不能删除，避免库存查询丢失商品名称")
    void productWithInventoryCannotBeDeleted() {
        jdbcTemplate.update("INSERT INTO inventory (warehouse_id, location_id, sku_id, qty) VALUES (?, ?, ?, 1)",
                warehouseA, locationId, skuId);

        BizException ex = assertThrows(BizException.class, () -> productService.delete(skuId));
        assertEquals(CommonErrorCode.RELATED_DATA_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("库区下还有库位时不能换仓库，避免库位和库区跨仓")
    void zoneWithLocationsCannotMoveWarehouse() {
        ZoneSaveDTO dto = zoneDto(warehouseB, ZONE_A);
        BizException ex = assertThrows(BizException.class,
                () -> zoneService.update(zoneA, dto));
        assertEquals(CommonErrorCode.RELATED_DATA_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    private Long createWarehouse(String code, String name) {
        jdbcTemplate.update("INSERT INTO warehouse (code, name, status) VALUES (?, ?, 'ENABLED')", code, name);
        return jdbcTemplate.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, code);
    }

    private Long createZone(Long warehouseId, String code, String name) {
        jdbcTemplate.update("INSERT INTO zone (warehouse_id, code, name, zone_type, sort_no, status) "
                + "VALUES (?, ?, ?, 'STORAGE', 0, 'ENABLED')", warehouseId, code, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM zone WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, code);
    }

    private LocationSaveDTO locationDto(Long warehouseId, Long zoneId, int capacity) {
        LocationSaveDTO dto = new LocationSaveDTO();
        dto.setWarehouseId(warehouseId);
        dto.setZoneId(zoneId);
        dto.setCode(LOC);
        dto.setName("一致性测试库位");
        dto.setLocationType("RACK");
        dto.setCapacity(capacity);
        dto.setStatus("ENABLED");
        return dto;
    }

    private ZoneSaveDTO zoneDto(Long warehouseId, String code) {
        ZoneSaveDTO dto = new ZoneSaveDTO();
        dto.setWarehouseId(warehouseId);
        dto.setCode(code);
        dto.setName("一致性测试库区");
        dto.setZoneType("STORAGE");
        dto.setSortNo(0);
        dto.setStatus("ENABLED");
        return dto;
    }
}