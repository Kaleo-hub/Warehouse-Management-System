package com.myproject.wmsserver.inbound.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InboundErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.inbound.dto.InboundItemDTO;
import com.myproject.wmsserver.inbound.dto.InboundOrderQuery;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.dto.ReceiveItemDTO;
import com.myproject.wmsserver.inbound.entity.InboundOrder;
import com.myproject.wmsserver.inbound.entity.InboundOrderItem;
import com.myproject.wmsserver.inbound.enums.InboundStatus;
import com.myproject.wmsserver.inbound.mapper.InboundOrderItemMapper;
import com.myproject.wmsserver.inbound.mapper.InboundOrderMapper;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderItemVO;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.myproject.wmsserver.basedata.service.PartnerService;
import com.myproject.wmsserver.basedata.vo.PartnerVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 入库单：建单 / 查询 / 收货 / 取消。
 *
 * 【贯穿本类的纪律】这里全程不碰库存。
 * 收货结束只是把单据推进到 RECEIVED，库存要等第 ③ 步「完成上架」才由 StockTaskService 去加。
 * 如果哪天你发现自己在收货代码里想调 InventoryService，那一定是理解错了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundOrderServiceImpl implements InboundOrderService {

    /** 发号器用的业务类型，和 sys_sequence.biz_type 对应 */
    private static final String BIZ_TYPE = "INBOUND_ORDER";
    private static final String ORDER_NO_PREFIX = "IN";

    private final InboundOrderMapper orderMapper;
    private final InboundOrderItemMapper itemMapper;
    /** 跨模块只能调对方的 Service，不能注入别人的 Mapper（docs/02 第 2 节） */
    private final ProductService productService;

    /** 往来单位主数据。跨模块只调 Service，不碰别人的 Mapper */
    private final PartnerService partnerService;
    private final SequenceService sequenceService;

    /* ============================ 查询 ============================ */

    @Override
    public PageResult<InboundOrderVO> page(InboundOrderQuery query) {
        Page<InboundOrderVO> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectOrderPage(page, query);
        // 列表不带明细，明细在详情接口才查
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public InboundOrderVO get(Long id) {
        InboundOrderVO vo = orderMapper.selectOrderDetail(id);
        if (vo == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        vo.setItems(itemMapper.selectByOrderId(id).stream().map(this::toItemVO).toList());
        return vo;
    }

    @Override
    public InboundOrderItemVO getItem(Long orderId, Long itemId) {
        InboundOrderItem item = itemMapper.selectById(itemId);
        // 校验归属：别把别的单据的明细当成本单的
        if (item == null || !orderId.equals(item.getOrderId())) {
            return null;
        }
        return toItemVO(item);
    }

    /* ============================ 建单 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InboundOrderSaveDTO dto) {
        // ① 批量取商品，一次查完（循环里单个 get 就是 N+1）
        List<Long> skuIds = dto.getItems().stream()
                .map(InboundItemDTO::getSkuId)
                .distinct()
                .toList();
        Map<Long, ProductVO> skuMap = productService.mapByIds(skuIds);

        // ② 校验每个 SKU 都存在且启用
        for (InboundItemDTO item : dto.getItems()) {
            ProductVO sku = skuMap.get(item.getSkuId());
            if (sku == null || !"ENABLED".equals(sku.getStatus())) {
                log.warn("建单引用了不可用商品 skuId={}", item.getSkuId());
                throw new BizException(InboundErrorCode.SKU_NOT_AVAILABLE);
            }
        }

        int totalExpectQty = dto.getItems().stream()
                .mapToInt(InboundItemDTO::getExpectQty)
                .sum();

        // ③ 表头
        InboundOrder order = new InboundOrder();
        // 单号由发号器统一生成，不用 MAX()+1，并发下不会重号
        order.setOrderNo(sequenceService.nextNo(BIZ_TYPE, ORDER_NO_PREFIX));
        order.setWarehouseId(dto.getWarehouseId());
        // 名称以主数据为准（填了 id 就忽略前端传的文本），没填才退回自由文本
        order.setSupplierName(resolvePartnerName(dto.getSupplierId(), dto.getSupplierName(), "SUPPLIER"));
        order.setSupplierId(dto.getSupplierId());
        order.setExpectArriveTime(dto.getExpectArriveTime());
        order.setStatus(InboundStatus.CREATED.name());
        order.setTotalExpectQty(totalExpectQty);
        order.setTotalReceivedQty(0);
        order.setRemark(blankToEmpty(dto.getRemark()));
        order.setCreateBy(UserContext.getUserId());
        orderMapper.insert(order);

        // ④ 明细：skuCode / skuName 在这里做快照
        for (InboundItemDTO dtoItem : dto.getItems()) {
            ProductVO sku = skuMap.get(dtoItem.getSkuId());

            InboundOrderItem item = new InboundOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(dtoItem.getSkuId());
            // 存快照而不是以后 join 商品表：商品改名了，历史单据也要显示当时的名字
            item.setSkuCode(sku.getSkuCode());
            item.setSkuName(sku.getName());
            item.setExpectQty(dtoItem.getExpectQty());
            item.setReceivedQty(0);
            item.setPutawayQty(0);
            item.setBatchNo("");
            item.setRemark(blankToEmpty(dtoItem.getRemark()));
            item.setCreateBy(UserContext.getUserId());
            itemMapper.insert(item);
        }

        log.info("创建入库单 id={} orderNo={} 明细={} 行 预计总量={}",
                order.getId(), order.getOrderNo(), dto.getItems().size(), totalExpectQty);
        return order.getId();
    }

    /* ============================ 收货 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long orderId, InboundReceiveDTO dto) {
        // ① 锁单据行。
        //    同一张单据不允许被两个请求同时收货，否则 total_received_qty 会算错。
        //    注意锁的是 inbound_order，不是 item —— 锁表头就够了，因为所有收货都要先经过这里。
        InboundOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        // ② 状态机校验：只有 CREATED / RECEIVING 能收货
        if (!InboundStatus.of(order.getStatus()).canReceive()) {
            log.warn("入库单状态不允许收货 orderNo={} status={}", order.getOrderNo(), order.getStatus());
            throw new BizException(InboundErrorCode.STATUS_NOT_ALLOWED);
        }

        Map<Long, InboundOrderItem> itemMap = itemMapper.selectByOrderId(orderId).stream()
                .collect(Collectors.toMap(InboundOrderItem::getId, item -> item));

        // ③ 先整体校验，任一行不合法就整单失败 —— 这是「整单原子」的关键
        for (ReceiveItemDTO input : dto.getItems()) {
            InboundOrderItem item = itemMap.get(input.getItemId());
            if (item == null) {
                // 传了别的单据的 itemId，直接拒绝
                log.warn("收货明细不属于该单据 orderId={} itemId={}", orderId, input.getItemId());
                throw new BizException(InboundErrorCode.ITEM_NOT_IN_ORDER);
            }
            int alreadyReceived = nz(item.getReceivedQty());
            int expect = nz(item.getExpectQty());
            if (alreadyReceived + input.getReceivedQty() > expect) {
                log.warn("实收超过预计 orderNo={} itemId={} 已收={} 本次={} 预计={}",
                        order.getOrderNo(), input.getItemId(), alreadyReceived,
                        input.getReceivedQty(), expect);
                throw new BizException(InboundErrorCode.RECEIVED_EXCEED_EXPECT);
            }
        }

        // ④ 校验全过了才落库
        for (ReceiveItemDTO input : dto.getItems()) {
            itemMapper.receiveItem(input.getItemId(), input.getReceivedQty(),
                    input.getBatchNo(), input.getProductionDate(), input.getExpiryDate());
        }

        // ⑤ 重算总量并推进状态
        List<InboundOrderItem> latest = itemMapper.selectByOrderId(orderId);
        int totalReceived = latest.stream().mapToInt(item -> nz(item.getReceivedQty())).sum();
        boolean allReceived = latest.stream()
                .allMatch(item -> nz(item.getReceivedQty()) >= nz(item.getExpectQty()));

        InboundStatus nextStatus = allReceived ? InboundStatus.RECEIVED : InboundStatus.RECEIVING;
        orderMapper.updateReceiveResult(orderId, nextStatus.name(), totalReceived, UserContext.getUserId());

        log.info("入库单收货完成 orderNo={} 本次={} 行 状态 {} -> {} 实收总量={}",
                order.getOrderNo(), dto.getItems().size(), order.getStatus(), nextStatus, totalReceived);

        // ★ 到此为止。整段代码里没有任何库存操作 —— 收货不增加库存，上架完成才增加。
    }

    /* ============================ 取消 ============================ */

    /**
     * 取消单据。
     *
     * 这里【没有】先 SELECT ... FOR UPDATE，因为不需要读原始数据做计算，
     * 一条带 status 条件的 UPDATE 本身就是原子的：
     * 谁先把 CREATED 改成 CANCELLED，另一个人的 UPDATE 影响行数就是 0。
     *
     * 对比 receive：那边要读明细、算总量，所以必须显式加锁。
     */
    @Override
    public void cancel(Long orderId) {
        int affected = orderMapper.cancel(orderId, UserContext.getUserId());
        if (affected == 0) {
            // 影响行数 0 有两种可能，要分清楚才能给对错误码
            if (orderMapper.selectById(orderId) == null) {
                throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
            }
            log.warn("入库单状态不允许取消 id={}", orderId);
            throw new BizException(InboundErrorCode.STATUS_NOT_ALLOWED);
        }
        log.info("入库单已取消 id={}", orderId);
    }

    /* ============================ 上架完成回写 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPutawayCompleted(Long orderId, Long orderItemId, int qty) {
        if (qty <= 0) {
            return;
        }

        // ① 累加明细的已上架数量。
        //    SQL 里带了 putaway_qty + qty <= received_qty 的条件，
        //    上架数不可能超过实收数 —— 真超了说明上层校验有漏，这里直接拦住。
        int affected = itemMapper.increasePutawayQty(orderItemId, qty);
        if (affected == 0) {
            log.warn("回写已上架数量失败 orderId={} orderItemId={} qty={}", orderId, orderItemId, qty);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }

        // ② 判断整单是否全部上架完
        List<InboundOrderItem> items = itemMapper.selectByOrderId(orderId);
        boolean allDone = !items.isEmpty() && items.stream()
                .allMatch(item -> nz(item.getPutawayQty()) >= nz(item.getReceivedQty()));

        if (allDone) {
            // CAS：只有当前还是 RECEIVED 才能推进，并发完成任务时只有一个能成功
            int updated = orderMapper.markPutawayDone(orderId, UserContext.getUserId());
            if (updated > 0) {
                log.info("入库单全部上架完成，状态推进到 PUTAWAY_DONE orderId={}", orderId);
            }
        }
    }

    /* ============================ 工具 ============================ */

    private InboundOrderItemVO toItemVO(InboundOrderItem item) {
        InboundOrderItemVO vo = new InboundOrderItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 取「往来单位名称快照」。
     *
     * 填了 id 就以主数据为准（名称由后端取，前端传什么都不算数），
     * 没填就退回自由文本 —— 历史单据和还在用老接口的调用方都还能跑。
     *
     * 【为什么校验类型和状态】供应商下拉里混进一个客户、或引用一个已停用的单位，
     * 单子建出来就再也对不上账了。这类脏数据要在入口挡住，别等对账时才发现。
     */
    private String resolvePartnerName(Long partnerId, String fallbackName, String expectType) {
        if (partnerId == null) {
            return blankToEmpty(fallbackName);
        }
        PartnerVO partner = partnerService.get(partnerId);
        if (!expectType.equals(partner.getPartnerType()) || !"ENABLED".equals(partner.getStatus())) {
            log.warn("引用了类型不符或已停用的往来单位 partnerId={} type={} status={}",
                    partnerId, partner.getPartnerType(), partner.getStatus());
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        // 名称以主数据为准
        return partner.getName();
    }
}
