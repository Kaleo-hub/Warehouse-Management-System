package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 待处理数必须跟当前仓库过滤走，单独用非事务测试避免看不到已提交数据。 */
@SpringBootTest
class InventorySummaryWarehouseScopeTest {

    private static final String WH_A = "T-SUMMARY-SCOPE-A";
    private static final String WH_B = "T-SUMMARY-SCOPE-B";
    private static final String ORDER_A = "T-SUMMARY-SCOPE-ORDER-A";
    private static final String ORDER_B = "T-SUMMARY-SCOPE-ORDER-B";

    @Autowired
    private InventoryQueryService inventoryQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long warehouseA;
    private Long warehouseB;

    @BeforeEach
    void setUp() {
        cleanup();
        warehouseA = createWarehouse(WH_A);
        warehouseB = createWarehouse(WH_B);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("总览待处理数按仓库过滤，不把其他仓的单据算进来")
    void pendingCountsShouldRespectWarehouseFilter() {
        jdbcTemplate.update("INSERT INTO inbound_order (order_no, warehouse_id, supplier_name, status) "
                + "VALUES (?, ?, '范围测试', 'CREATED')", ORDER_A, warehouseA);
        jdbcTemplate.update("INSERT INTO inbound_order (order_no, warehouse_id, supplier_name, status) "
                + "VALUES (?, ?, '范围测试', 'CREATED')", ORDER_B, warehouseB);

        InventorySummaryVO scoped = inventoryQueryService.summary(warehouseA);
        InventorySummaryVO global = inventoryQueryService.summary(null);

        assertEquals(1, scoped.getPendingInboundCount());
        assertTrue(global.getPendingInboundCount() >= 2);
    }

    private Long createWarehouse(String code) {
        jdbcTemplate.update("INSERT INTO warehouse (code, name, status) VALUES (?, ?, 'ENABLED')",
                code, "总览范围测试仓");
        return jdbcTemplate.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, code);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM inbound_order WHERE order_no IN (?, ?)", ORDER_A, ORDER_B);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code IN (?, ?)", WH_A, WH_B);
    }
}