package com.myproject.wmsserver.inventory;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.inventory.dto.FreezeDTO;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.dto.StocktakingDTO;
import com.myproject.wmsserver.inventory.dto.TransferDTO;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryAdjustService;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.inventory.vo.ReconcileVO;
import com.myproject.wmsserver.inventory.vo.StocktakingResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻结 / 解冻 / 移库 / 盘点 / 对账的验收测试。
 *
 * ========== 为什么不加 @Transactional ==========
 * 这几个操作里最有价值的验证点就是「事务回滚」和「对账修正」：
 *   移库的转入失败时，转出必须一起回滚；
 *   对账要真的把余额改掉，改完还要能被下一次对账看到。
 * 如果测试自己开着事务，这些改动会被测试事务包住，回滚根本验证不出来
 * （服务里的异常只会把测试事务标记成 rollback-only，而不是真的回滚）。
 * 所以这里不用测试事务，改成自建独立仓库 / 库位 / 商品，@AfterEach 物理清理。
 *
 * ========== 为什么不复用库里已有的 SKU ==========
 * 这些测试会直接改数据库（造假数据给对账去发现），
 * 一旦碰上真实数据，测试就不再是可重复的，还会污染别人。
 * 自建一套只属于本测试的仓库 + 库位 + 商品，测完删干净，跑多少次结果都一样。
 */
@SpringBootTest
class InventoryAdjustServiceTest {

    private static final String WH_CODE = "T-ADJ-WH";
    private static final String ZONE_CODE = "T-ADJ-ZONE";
    private static final String SKU_CODE = "T-ADJ-SKU";
    private static final String BATCH_NO = "B-ADJ-TEST";

    @Autowired
    private InventoryAdjustService inventoryAdjustService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 源库位 */
    private Long locationA;

    /** 目标库位（容量充足） */
    private Long locationB;

    /** 容量只有 5 的库位，用来验证容量不足时整笔移库回滚 */
    private Long locationSmall;

    /** 停用库位，不能作为移入目标 */
    private Long locationDisabled;

    private Long skuId;

    private Long warehouseId;

    /* ---------------------------- 建数据 / 清数据 ---------------------------- */

    @BeforeEach
    void setUp() {
        cleanup();
        jdbcTemplate.update(
                "INSERT INTO warehouse (code, name, address, status) VALUES (?, ?, '', 'ENABLED')",
                WH_CODE, "调整测试仓");
        warehouseId = jdbcTemplate.queryForObject(
                "SELECT id FROM warehouse WHERE code = ?", Long.class, WH_CODE);

        jdbcTemplate.update(
                "INSERT INTO zone (warehouse_id, code, name, zone_type) VALUES (?, ?, ?, 'STORAGE')",
                warehouseId, ZONE_CODE, "调整测试库区");
        Long zoneId = jdbcTemplate.queryForObject(
                "SELECT id FROM zone WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, ZONE_CODE);

        locationA = createLocation(zoneId, "T-A-01", 1000, "ENABLED");
        locationB = createLocation(zoneId, "T-B-01", 1000, "ENABLED");
        locationSmall = createLocation(zoneId, "T-S-01", 5, "ENABLED");
        locationDisabled = createLocation(zoneId, "T-D-01", 1000, "DISABLED");

        jdbcTemplate.update(
                "INSERT INTO product (sku_code, name, unit, status) VALUES (?, ?, '件', 'ENABLED')",
                SKU_CODE, "调整测试商品");
        skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, SKU_CODE);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    /** 物理删除本测试造的所有数据。必须是物理删除 —— 逻辑删除后唯一键还在，下次插不进去 */
    private void cleanup() {
        jdbcTemplate.update("DELETE FROM inventory_transaction "
                + "WHERE warehouse_id IN (SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM inventory "
                + "WHERE warehouse_id IN (SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM location "
                + "WHERE warehouse_id IN (SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM zone "
                + "WHERE warehouse_id IN (SELECT id FROM warehouse WHERE code = ?)", WH_CODE);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU_CODE);
        jdbcTemplate.update("DELETE FROM warehouse WHERE code = ?", WH_CODE);
    }

    private Long createLocation(Long zoneId, String code, int capacity, String status) {
        jdbcTemplate.update("INSERT INTO location "
                        + "(warehouse_id, zone_id, code, name, location_type, capacity, used_capacity, status) "
                        + "VALUES (?, ?, ?, ?, 'RACK', ?, 0, ?)",
                warehouseId, zoneId, code, "调整测试库位" + code, capacity, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM location WHERE warehouse_id = ? AND code = ?", Long.class, warehouseId, code);
    }

    /* ---------------------------- 断言用的小工具 ---------------------------- */

    /** 上架入库：测试里的“货是这么来的” */
    private void inbound(Long locationId, int qty) {
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId)
                .locationId(locationId)
                .skuId(skuId)
                .batchNo(BATCH_NO)
                .qtyChange(qty)
                .txType(InventoryTxType.PUTAWAY_IN)
                .sourceType("MANUAL")
                .operatorName("测试")
                .productionDate(LocalDate.of(2026, 1, 1))
                .expiryDate(LocalDate.of(2026, 12, 31))
                .build());
    }

    /** 模拟「这批货当初确实是上架进来的」，把库位已用容量摆到和库存一致 */
    private void seedUsedCapacity(Long locationId, int qty) {
        jdbcTemplate.update("UPDATE location SET used_capacity = ? WHERE id = ?", qty, locationId);
    }

    private int qty(Long locationId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, BATCH_NO);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int frozenQty(Long locationId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT frozen_qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, BATCH_NO);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int availableQty(Long locationId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT available_qty FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, BATCH_NO);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int usedCapacity(Long locationId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT used_capacity FROM location WHERE id = ?", Integer.class, locationId);
        return v == null ? 0 : v;
    }

    private boolean inventoryRowExists(Long locationId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Integer.class, locationId, skuId, BATCH_NO);
        return n != null && n > 0;
    }

    private Long inventoryId(Long locationId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                Long.class, locationId, skuId, BATCH_NO);
    }

    private int txCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE sku_id = ?", Integer.class, skuId);
        return n == null ? 0 : n;
    }

    private int txCountOfType(String txType) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE sku_id = ? AND tx_type = ?",
                Integer.class, skuId, txType);
        return n == null ? 0 : n;
    }

    private FreezeDTO freezeDto(int qty) {
        FreezeDTO dto = new FreezeDTO();
        dto.setQty(qty);
        return dto;
    }

    private TransferDTO transferDto(Long from, Long to, int qty) {
        TransferDTO dto = new TransferDTO();
        dto.setFromLocationId(from);
        dto.setToLocationId(to);
        dto.setSkuId(skuId);
        dto.setBatchNo(BATCH_NO);
        dto.setQty(qty);
        dto.setRemark("测试移库");
        return dto;
    }

    private StocktakingDTO stocktakingDto(Long locationId, int actualQty) {
        StocktakingDTO dto = new StocktakingDTO();
        dto.setLocationId(locationId);
        dto.setSkuId(skuId);
        dto.setBatchNo(BATCH_NO);
        dto.setActualQty(actualQty);
        return dto;
    }

    /* ---------------------------- 冻结 / 解冻 ---------------------------- */

    @Test
    @DisplayName("冻结：只是把可用数量挪进 frozen_qty，货还在库（qty 不变）")
    void freezeShouldMoveAvailableToFrozen() {
        inbound(locationA, 100);

        inventoryAdjustService.freeze(inventoryId(locationA), freezeDto(30));

        assertEquals(100, qty(locationA), "冻结不改 qty —— 货还在库里");
        assertEquals(30, frozenQty(locationA));
        assertEquals(70, availableQty(locationA), "可用 = qty - locked - frozen，由数据库生成列算出来");
        assertEquals(2, txCount(), "入库 1 条 + 冻结 1 条");
        assertEquals(1, txCountOfType("FREEZE"));
    }

    @Test
    @DisplayName("冻结超过可用数量：被不变量挡住，冻结量和流水都不变")
    void freezeMoreThanAvailableShouldFail() {
        inbound(locationA, 100);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.freeze(inventoryId(locationA), freezeDto(101)));
        assertEquals(InventoryErrorCode.AVAILABLE_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        assertEquals(0, frozenQty(locationA), "失败的冻结不能改到库存");
        assertEquals(1, txCount(), "失败的冻结不能留下流水");
    }

    @Test
    @DisplayName("解冻：frozen_qty 减回去，可用数量恢复")
    void unfreezeShouldRestoreAvailable() {
        inbound(locationA, 100);
        Long id = inventoryId(locationA);
        inventoryAdjustService.freeze(id, freezeDto(40));
        assertEquals(60, availableQty(locationA));

        inventoryAdjustService.unfreeze(id, freezeDto(40));

        assertEquals(0, frozenQty(locationA));
        assertEquals(100, availableQty(locationA));
        assertEquals(1, txCountOfType("UNFREEZE"));
    }

    @Test
    @DisplayName("解冻数量超过实际冻结量：报「可解冻数量不足」，而不是含糊的库存不足")
    void unfreezeMoreThanFrozenShouldFail() {
        inbound(locationA, 100);
        Long id = inventoryId(locationA);
        inventoryAdjustService.freeze(id, freezeDto(10));

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.unfreeze(id, freezeDto(20)));
        assertEquals(InventoryErrorCode.FREEZE_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        assertEquals(10, frozenQty(locationA), "失败的解冻不能改到库存");
        assertEquals(0, txCountOfType("UNFREEZE"));
    }

    @Test
    @DisplayName("★ 冻结的货不能移库（BR-005）：全部冻结后移库被可用数量挡住")
    void frozenStockCannotBeTransferred() {
        inbound(locationA, 100);
        inventoryAdjustService.freeze(inventoryId(locationA), freezeDto(100));

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.transfer(transferDto(locationA, locationB, 10)));
        assertEquals(InventoryErrorCode.AVAILABLE_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        assertEquals(100, qty(locationA), "源库位不能少货");
        assertFalse(inventoryRowExists(locationB), "目标库位不该凭空多出一行库存");
        assertEquals(0, txCountOfType("TRANSFER_OUT"));
    }

    /* ---------------------------- 移库 ---------------------------- */

    @Test
    @DisplayName("★ 移库：一个事务里转出 -n、转入 +n，总量守恒，目标库位自动建行")
    void transferShouldMoveQtyAndKeepTotal() {
        inbound(locationA, 100);
        seedUsedCapacity(locationA, 100);

        inventoryAdjustService.transfer(transferDto(locationA, locationB, 30));

        assertEquals(70, qty(locationA), "源库位扣掉 30");
        assertEquals(30, qty(locationB), "目标库位加上 30（这一行是自动建出来的）");
        assertEquals(100, qty(locationA) + qty(locationB), "移库只是换位置，总量不变");
        assertEquals(1, txCountOfType("TRANSFER_OUT"));
        assertEquals(1, txCountOfType("TRANSFER_IN"));

        // 库位占用一减一加，成对调整，否则每移一次库占用率就虚高一次
        assertEquals(70, usedCapacity(locationA));
        assertEquals(30, usedCapacity(locationB));
    }

    @Test
    @DisplayName("移库会把生产日期 / 到期日期一起带过去，目标库位不会变成「没有效期的货」")
    void transferShouldCarryExpiryInfo() {
        inbound(locationA, 100);

        inventoryAdjustService.transfer(transferDto(locationA, locationB, 10));

        LocalDate productionDate = jdbcTemplate.queryForObject(
                "SELECT production_date FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                LocalDate.class, locationB, skuId, BATCH_NO);
        LocalDate expiryDate = jdbcTemplate.queryForObject(
                "SELECT expiry_date FROM inventory WHERE location_id = ? AND sku_id = ? AND batch_no = ?",
                LocalDate.class, locationB, skuId, BATCH_NO);
        assertEquals(LocalDate.of(2026, 1, 1), productionDate);
        assertEquals(LocalDate.of(2026, 12, 31), expiryDate);
    }

    @Test
    @DisplayName("移出和移入是同一个库位：直接报错，不做任何变动")
    void transferToSameLocationShouldFail() {
        inbound(locationA, 100);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.transfer(transferDto(locationA, locationA, 10)));
        assertEquals(InventoryErrorCode.TRANSFER_SAME_LOCATION.getCode(), ex.getErrorCode().getCode());
        assertEquals(100, qty(locationA));
    }

    @Test
    @DisplayName("移入停用库位：拒绝（停用的库位不允许再放货）")
    void transferToDisabledLocationShouldFail() {
        inbound(locationA, 100);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.transfer(transferDto(locationA, locationDisabled, 10)));
        assertEquals(InventoryErrorCode.LOCATION_DISABLED.getCode(), ex.getErrorCode().getCode());
        assertEquals(100, qty(locationA));
        assertFalse(inventoryRowExists(locationDisabled));
    }

    @Test
    @DisplayName("★ 移库的原子性：目标库位容量不足时，转出的库存和库位占用一起回滚")
    void transferShouldRollbackWhenTargetOverCapacity() {
        inbound(locationA, 100);
        seedUsedCapacity(locationA, 100);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.transfer(transferDto(locationA, locationSmall, 100)));
        assertEquals(CommonErrorCode.LOCATION_CAPACITY_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        assertEquals(100, qty(locationA), "转入失败，转出必须一起回滚 —— 否则货就凭空消失了");
        assertEquals(100, usedCapacity(locationA), "库位占用的减少也要跟着回滚，回到移库前的 100");

        assertEquals(0, usedCapacity(locationSmall), "目标库位占用不能留下痕迹");
        assertFalse(inventoryRowExists(locationSmall), "目标库位不能留下一行 0 库存");
        assertEquals(1, txCount(), "只能有入库那一条流水，移库的两条都不该留下");
    }

    /* ---------------------------- 盘点 ---------------------------- */

    @Test
    @DisplayName("盘盈：实盘比系统多，按差异写 STOCKTAKING_GAIN")
    void stocktakingGainShouldIncreaseQty() {
        inbound(locationA, 100);

        StocktakingResultVO result = inventoryAdjustService.stocktake(stocktakingDto(locationA, 120));

        assertEquals(100, result.getSystemQty());
        assertEquals(120, result.getActualQty());
        assertEquals(20, result.getDiffQty());
        assertEquals("STOCKTAKING_GAIN", result.getTxType());
        assertTrue(result.isChanged());

        assertEquals(120, qty(locationA));
        assertEquals(1, txCountOfType("STOCKTAKING_GAIN"));
    }

    @Test
    @DisplayName("盘亏：实盘比系统少，按差异写 STOCKTAKING_LOSS")
    void stocktakingLossShouldDecreaseQty() {
        inbound(locationA, 100);

        StocktakingResultVO result = inventoryAdjustService.stocktake(stocktakingDto(locationA, 80));

        assertEquals(-20, result.getDiffQty());
        assertEquals("STOCKTAKING_LOSS", result.getTxType());
        assertEquals(80, qty(locationA));
        assertEquals(1, txCountOfType("STOCKTAKING_LOSS"));
    }

    @Test
    @DisplayName("账实相符：不写流水（流水记的是变动，0 变动没有信息量）")
    void stocktakingSameQtyShouldNotWriteTransaction() {
        inbound(locationA, 100);

        StocktakingResultVO result = inventoryAdjustService.stocktake(stocktakingDto(locationA, 100));

        assertEquals(0, result.getDiffQty());
        assertFalse(result.isChanged());
        assertNull(result.getTxType(), "没有变动就没有流水类型");
        assertEquals(1, txCount(), "只应该有入库那一条流水");
    }

    @Test
    @DisplayName("★ 盘亏不能把被订单锁定的货盘没：实盘数量小于锁定数量时拒绝")
    void stocktakingLossBelowLockedShouldFail() {
        inbound(locationA, 100);
        // 先分配锁定 30（出库分配的动作，这里直接调内核模拟）
        inventoryService.applyChange(InventoryChangeCommand.builder()
                .warehouseId(warehouseId)
                .locationId(locationA)
                .skuId(skuId)
                .batchNo(BATCH_NO)
                .lockedChange(30)
                .txType(InventoryTxType.ALLOCATE_LOCK)
                .sourceType("MANUAL")
                .createIfAbsent(false)
                .build());

        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.stocktake(stocktakingDto(locationA, 20)));
        assertEquals(InventoryErrorCode.AVAILABLE_NOT_ENOUGH.getCode(), ex.getErrorCode().getCode());

        assertEquals(100, qty(locationA), "失败的盘点不能改到库存");
    }

    @Test
    @DisplayName("盘点一个不存在的库存行：报「库存记录不存在」，不能凭空建账")
    void stocktakingMissingInventoryShouldFail() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryAdjustService.stocktake(stocktakingDto(locationA, 10)));
        assertEquals(InventoryErrorCode.STOCK_NOT_FOUND.getCode(), ex.getErrorCode().getCode());
        assertFalse(inventoryRowExists(locationA), "盘点不能自动建库存行");
    }

    /* ---------------------------- 对账 ---------------------------- */

    @Test
    @DisplayName("★ 对账：能查出被手工改坏的余额，autoFix=true 按流水修回来，且修完之后账是真的平了")
    void reconcileShouldDetectAndFixBrokenBalance() {
        inbound(locationA, 100);
        inbound(locationA, 50);
        Long id = inventoryId(locationA);

        // 绕过所有业务逻辑，直接改库 —— 模拟「程序 bug / 有人手工改过库」造成的账不平。
        // 这正是对账存在的意义：不假设所有写路径都正确，而是能发现并纠正错误。
        jdbcTemplate.update("UPDATE inventory SET qty = 999 WHERE id = ?", id);

        // ① 只检查，不改数
        ReconcileVO checkOnly = inventoryAdjustService.reconcile(false);
        ReconcileVO.Mismatch mismatch = findMismatch(checkOnly, id);
        assertNotNull(mismatch, "改坏的余额必须被扫出来");
        assertEquals(999, mismatch.getBalanceQty(), "余额是 999");
        assertEquals(150, mismatch.getCalculatedQty(), "按流水算出来是 100 + 50 = 150");
        assertEquals("SKIPPED", mismatch.getFixResult());
        assertEquals(999, qty(locationA), "autoFix=false 时不能改数据");
        assertEquals(2, txCount());

        // ② 按流水修正
        ReconcileVO fixed = inventoryAdjustService.reconcile(true);
        ReconcileVO.Mismatch fixedRow = findMismatch(fixed, id);
        assertNotNull(fixedRow);
        assertEquals("FIXED", fixedRow.getFixResult());
        assertEquals(150, qty(locationA), "以流水为准：余额被改成 150");
        assertEquals(2, txCount(), "修正余额不是库存变动，【不能】写库存流水："
                + "写了的话流水 SUM 就变成 150-849=-699，下一次对账又对不平，永远收敛不了");
        assertTrue(fixed.getFixedCount() >= 1);

        // ③ 再对一次：必须真的平了。
        // 这一步是这次测试最重要的断言 —— 它证明「修正」是收敛的，
        // 跑 N 次对账结果都一样，而不是修一次变一个新窟窿。
        ReconcileVO after = inventoryAdjustService.reconcile(false);
        assertNull(findMismatch(after, id), "修完之后这条不应该再出现在不一致列表里");
    }

    @Test
    @DisplayName("对账：锁定数量对不上也能查出来（不是只看 qty）")
    void reconcileShouldAlsoCheckLockedQty() {
        inbound(locationA, 100);
        Long id = inventoryId(locationA);
        // 手工把 locked_qty 改成 30，但流水里从来没有过锁定动作
        jdbcTemplate.update("UPDATE inventory SET locked_qty = 30 WHERE id = ?", id);

        ReconcileVO result = inventoryAdjustService.reconcile(false);

        ReconcileVO.Mismatch mismatch = findMismatch(result, id);
        assertNotNull(mismatch, "锁定数量对不上也要能被发现");
        assertEquals(30, mismatch.getBalanceLockedQty());
        assertEquals(0, mismatch.getCalculatedLockedQty());
    }

    private ReconcileVO.Mismatch findMismatch(ReconcileVO vo, Long inventoryId) {
        return vo.getMismatches().stream()
                .filter(m -> inventoryId.equals(m.getInventoryId()))
                .findFirst()
                .orElse(null);
    }
}