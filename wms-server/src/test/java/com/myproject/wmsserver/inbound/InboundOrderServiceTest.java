package com.myproject.wmsserver.inbound;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InboundErrorCode;
import com.myproject.wmsserver.inbound.dto.InboundItemDTO;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.dto.ReceiveItemDTO;
import com.myproject.wmsserver.inbound.enums.InboundStatus;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
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
 * 入库单建单 / 收货 的验收测试。
 *
 * 跑之前确保本地 MySQL 已启动、DB_PASSWORD 已设置。
 * 类上的 @Transactional 让每个用例跑完自动回滚。
 */
@SpringBootTest
@Transactional
class InboundOrderServiceTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long SKU_ID_1 = 1L;
    private static final Long SKU_ID_2 = 2L;

    @Autowired
    private InboundOrderService inboundOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* ---------------------------- 工具 ---------------------------- */

    private InboundOrderSaveDTO buildOrder(int qty1, int qty2) {
        InboundOrderSaveDTO dto = new InboundOrderSaveDTO();
        dto.setWarehouseId(WAREHOUSE_ID);
        dto.setSupplierName("测试供应商");

        InboundItemDTO item1 = new InboundItemDTO();
        item1.setSkuId(SKU_ID_1);
        item1.setExpectQty(qty1);

        InboundItemDTO item2 = new InboundItemDTO();
        item2.setSkuId(SKU_ID_2);
        item2.setExpectQty(qty2);

        dto.setItems(List.of(item1, item2));
        return dto;
    }

    private InboundReceiveDTO buildReceive(Long itemId, int qty) {
        ReceiveItemDTO item = new ReceiveItemDTO();
        item.setItemId(itemId);
        item.setReceivedQty(qty);
        item.setBatchNo("B-TEST-001");

        InboundReceiveDTO dto = new InboundReceiveDTO();
        dto.setItems(List.of(item));
        return dto;
    }

    private int countInventory() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory", Integer.class);
        return n == null ? 0 : n;
    }

    private int countTransaction() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_transaction", Integer.class);
        return n == null ? 0 : n;
    }

    /* ---------------------------- 建单 ---------------------------- */

    @Test
    @DisplayName("建单：生成单号、汇总预计总量、SKU 快照落库、状态为 CREATED")
    void createOrder() {
        Long id = inboundOrderService.create(buildOrder(100, 50));

        InboundOrderVO vo = inboundOrderService.get(id);
        assertNotNull(vo.getOrderNo());
        assertTrue(vo.getOrderNo().startsWith("IN"), "单号应以 IN 开头");
        assertEquals(16, vo.getOrderNo().length(), "单号格式应为 IN(2) + 日期(8) + 序号(6) = 16 位");
        assertEquals(InboundStatus.CREATED.name(), vo.getStatus());
        assertEquals(150, vo.getTotalExpectQty(), "预计总量应该是明细之和");
        assertEquals(0, vo.getTotalReceivedQty());

        assertEquals(2, vo.getItems().size());
        assertEquals("SKU0001", vo.getItems().get(0).getSkuCode(), "skuCode 要落快照");
        assertNotNull(vo.getItems().get(0).getSkuName(), "skuName 要落快照");
        assertEquals(100, vo.getItems().get(0).getExpectQty());
    }

    @Test
    @DisplayName("建单：连续两张单的单号必须递增且不重复")
    void orderNoShouldBeUnique() {
        String no1 = inboundOrderService.get(inboundOrderService.create(buildOrder(10, 10))).getOrderNo();
        String no2 = inboundOrderService.get(inboundOrderService.create(buildOrder(10, 10))).getOrderNo();
        assertTrue(no1.compareTo(no2) < 0, "第二张单的单号应该更大：" + no1 + " vs " + no2);
    }

    @Test
    @DisplayName("建单：引用了不存在的商品应该被拒绝")
    void rejectUnknownSku() {
        InboundOrderSaveDTO dto = buildOrder(10, 10);
        dto.getItems().get(0).setSkuId(999999L);

        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.create(dto));
        assertEquals(InboundErrorCode.SKU_NOT_AVAILABLE.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 收货 ---------------------------- */

    @Test
    @DisplayName("★ 收货不增加库存 —— 这是本模块最重要的一条纪律")
    void receiveMustNotTouchInventory() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        InboundOrderVO before = inboundOrderService.get(id);

        int invBefore = countInventory();
        int txBefore = countTransaction();

        inboundOrderService.receive(id, buildReceive(before.getItems().get(0).getId(), 100));

        assertEquals(invBefore, countInventory(), "收货绝不能新增库存记录");
        assertEquals(txBefore, countTransaction(), "收货绝不能写库存流水");
    }

    @Test
    @DisplayName("全额收货 → 状态推进到 RECEIVED")
    void receiveAllShouldFinish() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        InboundOrderVO order = inboundOrderService.get(id);

        inboundOrderService.receive(id, buildReceive(order.getItems().get(0).getId(), 100));
        assertEquals(InboundStatus.RECEIVING.name(), inboundOrderService.get(id).getStatus(),
                "只收了一行，状态应该是收货中");

        inboundOrderService.receive(id, buildReceive(order.getItems().get(1).getId(), 50));
        InboundOrderVO after = inboundOrderService.get(id);
        assertEquals(InboundStatus.RECEIVED.name(), after.getStatus(), "全收完应该是已收货");
        assertEquals(150, after.getTotalReceivedQty());
    }

    @Test
    @DisplayName("分批收货：先收 30 再收 70，实收数量累加而不是覆盖")
    void receiveInBatches() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        Long itemId = inboundOrderService.get(id).getItems().get(0).getId();

        inboundOrderService.receive(id, buildReceive(itemId, 30));
        assertEquals(30, inboundOrderService.get(id).getItems().get(0).getReceivedQty());

        inboundOrderService.receive(id, buildReceive(itemId, 70));
        assertEquals(100, inboundOrderService.get(id).getItems().get(0).getReceivedQty());
        assertEquals(100, inboundOrderService.get(id).getTotalReceivedQty());
    }

    @Test
    @DisplayName("实收超过预计 → 40004，且整单回滚（另一行也不能被改）")
    void rejectOverReceive() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        InboundOrderVO order = inboundOrderService.get(id);

        InboundReceiveDTO dto = new InboundReceiveDTO();
        ReceiveItemDTO ok = new ReceiveItemDTO();
        ok.setItemId(order.getItems().get(0).getId());
        ok.setReceivedQty(10);
        ReceiveItemDTO bad = new ReceiveItemDTO();
        bad.setItemId(order.getItems().get(1).getId());
        bad.setReceivedQty(999);          // 超过预计的 50
        dto.setItems(List.of(ok, bad));

        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.receive(id, dto));
        assertEquals(InboundErrorCode.RECEIVED_EXCEED_EXPECT.getCode(), ex.getErrorCode().getCode());

        InboundOrderVO after = inboundOrderService.get(id);
        assertEquals(0, after.getTotalReceivedQty(), "整单回滚，合法的那一行也不能落库");
        assertEquals(InboundStatus.CREATED.name(), after.getStatus());
    }

    @Test
    @DisplayName("收货明细传了别的单据的 itemId → 40005")
    void rejectForeignItem() {
        Long id1 = inboundOrderService.create(buildOrder(100, 50));
        Long id2 = inboundOrderService.create(buildOrder(10, 10));
        Long foreignItemId = inboundOrderService.get(id2).getItems().get(0).getId();

        BizException ex = assertThrows(BizException.class,
                () -> inboundOrderService.receive(id1, buildReceive(foreignItemId, 5)));
        assertEquals(InboundErrorCode.ITEM_NOT_IN_ORDER.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("已收货的单据不能再收货")
    void cannotReceiveAgain() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        InboundOrderVO order = inboundOrderService.get(id);
        inboundOrderService.receive(id, buildReceive(order.getItems().get(0).getId(), 100));
        inboundOrderService.receive(id, buildReceive(order.getItems().get(1).getId(), 50));

        BizException ex = assertThrows(BizException.class,
                () -> inboundOrderService.receive(id, buildReceive(order.getItems().get(0).getId(), 1)));
        assertEquals(InboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("同一请求里重复出现同一条入库明细：直接拒绝，不能把数量累加两次")
    void duplicateItemInOneReceiveShouldBeRejected() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        Long itemId = inboundOrderService.get(id).getItems().get(0).getId();

        ReceiveItemDTO first = new ReceiveItemDTO();
        first.setItemId(itemId);
        first.setReceivedQty(60);
        first.setBatchNo("B-DUP-1");
        ReceiveItemDTO second = new ReceiveItemDTO();
        second.setItemId(itemId);
        second.setReceivedQty(60);
        second.setBatchNo("B-DUP-1");

        InboundReceiveDTO dto = new InboundReceiveDTO();
        dto.setItems(List.of(first, second));

        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.receive(id, dto));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), ex.getErrorCode().getCode());
        assertEquals(0, inboundOrderService.get(id).getItems().get(0).getReceivedQty());
    }

    @Test
    @DisplayName("建单仓库不存在：拒绝创建，避免产生没有归属的裸单据")
    void rejectUnknownWarehouse() {
        InboundOrderSaveDTO dto = buildOrder(10, 10);
        dto.setWarehouseId(99999999L);

        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.create(dto));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), ex.getErrorCode().getCode());
    }

    /* ---------------------------- 取消 ---------------------------- */

    @Test
    @DisplayName("CREATED 可以取消")
    void cancelCreatedOrder() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        inboundOrderService.cancel(id);
        assertEquals(InboundStatus.CANCELLED.name(), inboundOrderService.get(id).getStatus());
    }

    @Test
    @DisplayName("已经收过货的单据不能取消 → 40002")
    void cannotCancelAfterReceive() {
        Long id = inboundOrderService.create(buildOrder(100, 50));
        Long itemId = inboundOrderService.get(id).getItems().get(0).getId();
        inboundOrderService.receive(id, buildReceive(itemId, 10));
        assertEquals(InboundStatus.RECEIVING.name(), inboundOrderService.get(id).getStatus());

        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.cancel(id));
        assertEquals(InboundErrorCode.STATUS_NOT_ALLOWED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("取消不存在的单据 → 10004")
    void cancelNotFound() {
        BizException ex = assertThrows(BizException.class, () -> inboundOrderService.cancel(99999999L));
        assertEquals(10004, ex.getErrorCode().getCode());
    }
}
