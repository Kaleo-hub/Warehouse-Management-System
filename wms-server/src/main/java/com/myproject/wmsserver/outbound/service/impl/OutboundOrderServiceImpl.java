package com.myproject.wmsserver.outbound.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.outbound.dto.OutboundItemDTO;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.entity.OutboundAllocate;
import com.myproject.wmsserver.outbound.entity.OutboundOrder;
import com.myproject.wmsserver.outbound.entity.OutboundOrderItem;
import com.myproject.wmsserver.outbound.enums.AllocateStatus;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.mapper.OutboundAllocateMapper;
import com.myproject.wmsserver.outbound.mapper.OutboundOrderItemMapper;
import com.myproject.wmsserver.outbound.mapper.OutboundOrderMapper;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.outbound.vo.OutboundAllocateVO;
import com.myproject.wmsserver.outbound.vo.OutboundOrderItemVO;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.myproject.wmsserver.basedata.service.PartnerService;
import com.myproject.wmsserver.basedata.vo.PartnerVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 出库单：建单 / 分配 / 释放 / 拣货回调 / 发货。
 *
 * 【依赖方向】outbound 不依赖 task —— 生成拣货任务由 task 模块负责，
 * 拣货完成时 task 反过来调本类的 onPickCompleted 回调。
 * 依赖只有 task → outbound 一条，不会形成循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundOrderServiceImpl implements OutboundOrderService {

    private static final String BIZ_TYPE = "OUTBOUND_ORDER";
    private static final String ORDER_NO_PREFIX = "OUT";
    /** 库存流水的来源类型 */
    private static final String SOURCE_OUTBOUND = "OUTBOUND_ORDER";

    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderItemMapper itemMapper;
    private final OutboundAllocateMapper allocateMapper;
    private final ProductService productService;

    /** 往来单位主数据。跨模块只调 Service，不碰别人的 Mapper */
    private final PartnerService partnerService;
    private final SequenceService sequenceService;
    private final InventoryService inventoryService;

    /**
     * 库位占用是「派生计数」：上架时加、发货/移出/盘亏时减。
     * 跨模块只能调 Service（不能直接注入 LocationMapper），见 docs/02 的依赖方向约定。
     */
    private final LocationService locationService;
    private final InventoryQueryService inventoryQueryService;

    /* ============================ 查询 ============================ */

    @Override
    public PageResult<OutboundOrderVO> page(OutboundOrderQuery query) {
        Page<OutboundOrderVO> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectOrderPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public OutboundOrderVO get(Long id) {
        OutboundOrderVO vo = orderMapper.selectOrderDetail(id);
        if (vo == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        vo.setItems(itemMapper.selectByOrderId(id).stream().map(this::toItemVO).toList());
        vo.setAllocates(allocateMapper.selectVOByOrderId(id));
        return vo;
    }

    /* ============================ 建单 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OutboundOrderSaveDTO dto) {
        List<Long> skuIds = dto.getItems().stream().map(OutboundItemDTO::getSkuId).distinct().toList();
        Map<Long, ProductVO> skuMap = productService.mapByIds(skuIds);

        for (OutboundItemDTO item : dto.getItems()) {
            ProductVO sku = skuMap.get(item.getSkuId());
            if (sku == null || !"ENABLED".equals(sku.getStatus())) {
                throw new BizException(CommonErrorCode.PARAM_INVALID);
            }
        }

        int totalQty = dto.getItems().stream().mapToInt(OutboundItemDTO::getQty).sum();

        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(sequenceService.nextNo(BIZ_TYPE, ORDER_NO_PREFIX));
        order.setWarehouseId(dto.getWarehouseId());
        // 名称以主数据为准（填了 id 就忽略前端传的文本），没填才退回自由文本
        order.setCustomerName(resolvePartnerName(dto.getCustomerId(), dto.getCustomerName(), "CUSTOMER"));
        order.setCustomerId(dto.getCustomerId());
        order.setReceiveAddress(blankToEmpty(dto.getReceiveAddress()));
        order.setExpectShipTime(dto.getExpectShipTime());
        order.setStatus(OutboundStatus.CREATED.name());
        order.setTotalQty(totalQty);
        order.setAllocatedQty(0);
        order.setPickedQty(0);
        order.setRemark(blankToEmpty(dto.getRemark()));
        order.setCreateBy(UserContext.getUserId());
        orderMapper.insert(order);

        for (OutboundItemDTO dtoItem : dto.getItems()) {
            ProductVO sku = skuMap.get(dtoItem.getSkuId());

            OutboundOrderItem item = new OutboundOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(dtoItem.getSkuId());
            // 快照：商品改名不影响历史单据
            item.setSkuCode(sku.getSkuCode());
            item.setSkuName(sku.getName());
            item.setQty(dtoItem.getQty());
            item.setAllocatedQty(0);
            item.setPickedQty(0);
            item.setRemark(blankToEmpty(dtoItem.getRemark()));
            item.setCreateBy(UserContext.getUserId());
            itemMapper.insert(item);
        }

        log.info("创建出库单 id={} orderNo={} 明细={} 行 需求总量={}",
                order.getId(), order.getOrderNo(), dto.getItems().size(), totalQty);
        return order.getId();
    }

    /* ============================ 分配 ★ ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocate(Long orderId) {
        // ① 锁单据行：要读明细、算总量、写回，中间不能被插队
        OutboundOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!OutboundStatus.of(order.getStatus()).canAllocate()) {
            log.warn("出库单状态不允许分配 orderNo={} status={}", order.getOrderNo(), order.getStatus());
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        List<OutboundOrderItem> items = itemMapper.selectByOrderId(orderId);
        int totalAllocated = 0;

        for (OutboundOrderItem item : items) {
            int need = nz(item.getQty());
            if (need <= 0) {
                continue;
            }

            /*
             * 按 FIFO 拿候选批次（BR-004）：入库早的优先、生产日期早的优先。
             * 这一次查询【不加锁】—— 真正的并发保护在下面 applyChange 里。
             * 读到之后可能被别人抢走，那时 applyChange 会校验失败抛异常，整单回滚。
             * 这是"乐观读 + 悲观写 + 写时重新校验"的典型组合。
             */
            List<Inventory> candidates = inventoryQueryService.listFifoAvailable(
                    order.getWarehouseId(), item.getSkuId());

            for (Inventory candidate : candidates) {
                if (need <= 0) {
                    break;
                }

                int available = nz(candidate.getQty())
                        - nz(candidate.getLockedQty())
                        - nz(candidate.getFrozenQty());
                if (available <= 0) {
                    continue;
                }
                int take = Math.min(need, available);

                // 真正锁定：applyChange 会加行锁并重新校验可用量，不满足直接抛异常
                inventoryService.applyChange(InventoryChangeCommand.builder()
                        .warehouseId(order.getWarehouseId())
                        .locationId(candidate.getLocationId())
                        .skuId(item.getSkuId())
                        .batchNo(candidate.getBatchNo())
                        .lockedChange(take)
                        .txType(InventoryTxType.ALLOCATE_LOCK)
                        .sourceType(SOURCE_OUTBOUND)
                        .sourceId(orderId)
                        .sourceNo(order.getOrderNo())
                        .remark("出库分配锁定")
                        // 关键：出库不能凭空创建库存行。没库存就是没库存
                        .createIfAbsent(false)
                        .build());

                OutboundAllocate allocate = new OutboundAllocate();
                allocate.setOrderId(orderId);
                allocate.setOrderItemId(item.getId());
                allocate.setInventoryId(candidate.getId());
                allocate.setWarehouseId(order.getWarehouseId());
                allocate.setLocationId(candidate.getLocationId());
                allocate.setSkuId(item.getSkuId());
                allocate.setBatchNo(candidate.getBatchNo());
                allocate.setAllocatedQty(take);
                allocate.setPickedQty(0);
                allocate.setStatus(AllocateStatus.LOCKED.name());
                allocate.setCreateBy(UserContext.getUserId());
                allocateMapper.insert(allocate);

                need -= take;
                totalAllocated += take;
            }

            // ② 整单原子：任一明细分配不满就整单回滚（前面锁的库存也会一起还回去）
            if (need > 0) {
                log.warn("可用库存不足，分配失败 orderNo={} skuId={} 还差={}",
                        order.getOrderNo(), item.getSkuId(), need);
                throw new BizException(OutboundErrorCode.STOCK_NOT_ENOUGH_FOR_ALLOCATE);
            }

            itemMapper.updateAllocatedQty(item.getId(), nz(item.getQty()));
        }

        // ③ 推进状态（CAS）
        int affected = orderMapper.markAllocated(orderId, totalAllocated, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        log.info("出库单分配完成 orderNo={} 分配总量={}", order.getOrderNo(), totalAllocated);
    }

    /* ============================ 释放 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long orderId) {
        OutboundOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!OutboundStatus.of(order.getStatus()).canRelease()) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        // 还有没做完的拣货任务就不能释放，否则作业员会照着失效的任务去拣货
        if (orderMapper.countUnfinishedPickTask(orderId) > 0) {
            throw new BizException(OutboundErrorCode.PICK_TASK_UNFINISHED);
        }

        List<OutboundAllocate> allocates =
                allocateMapper.selectByOrderIdAndStatus(orderId, AllocateStatus.LOCKED.name());

        for (OutboundAllocate allocate : allocates) {
            // 把锁定的数量还回库存
            inventoryService.applyChange(InventoryChangeCommand.builder()
                    .warehouseId(allocate.getWarehouseId())
                    .locationId(allocate.getLocationId())
                    .skuId(allocate.getSkuId())
                    .batchNo(allocate.getBatchNo())
                    .lockedChange(-nz(allocate.getAllocatedQty()))
                    .txType(InventoryTxType.ALLOCATE_RELEASE)
                    .sourceType(SOURCE_OUTBOUND)
                    .sourceId(orderId)
                    .sourceNo(order.getOrderNo())
                    .remark("释放出库分配")
                    .createIfAbsent(false)
                    .build());

            allocateMapper.markReleased(allocate.getId());
        }

        itemMapper.resetAllocatedQty(orderId);
        int affected = orderMapper.markReleased(orderId, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        log.info("出库单释放分配 orderNo={} 释放 {} 条", order.getOrderNo(), allocates.size());
    }

    /* ============================ 取消 ============================ */

    @Override
    public void cancel(Long orderId) {
        int affected = orderMapper.cancel(orderId, UserContext.getUserId());
        if (affected == 0) {
            if (orderMapper.selectById(orderId) == null) {
                throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
            }
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }
        log.info("出库单已取消 id={}", orderId);
    }

    /* ============================ 发货 ★ ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long orderId) {
        OutboundOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!OutboundStatus.of(order.getStatus()).canShip()) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        List<OutboundAllocate> allocates =
                allocateMapper.selectByOrderIdAndStatus(orderId, AllocateStatus.PICKED.name());
        if (allocates.isEmpty()) {
            throw new BizException(OutboundErrorCode.NO_ALLOCATION_TO_PICK);
        }

        /*
         * 三阶段模型的最后一步：qty 和 locked_qty 一起减。
         *
         *   分配：qty 不变，locked +n   （货还在货架上，只是账上占住）
         *   拣货：都不变                （货已经在推车上，账面先不动）
         *   发货：qty -n，locked -n     （账实核销）
         *
         * 减完之后 available = qty - locked - frozen 保持不变 —— 这是对的，
         * 因为这批货本来就已经从可用量里扣掉了（分配那一步扣的）。
         */
        for (OutboundAllocate allocate : allocates) {
            int qty = nz(allocate.getAllocatedQty());
            inventoryService.applyChange(InventoryChangeCommand.builder()
                    .warehouseId(allocate.getWarehouseId())
                    .locationId(allocate.getLocationId())
                    .skuId(allocate.getSkuId())
                    .batchNo(allocate.getBatchNo())
                    .qtyChange(-qty)
                    .lockedChange(-qty)
                    .txType(InventoryTxType.OUTBOUND_OUT)
                    .sourceType(SOURCE_OUTBOUND)
                    .sourceId(orderId)
                    .sourceNo(order.getOrderNo())
                    .remark("出库发货")
                    .createIfAbsent(false)
                    .build());

            /*
             * 库位占用要跟着减。
             *
             * 上架时 increaseUsedCapacity 加了 qty，发货把货真的搬出去了，
             * 这里就必须减回来 —— 否则每发一次货，库位就"少"了一点容量，
             * 看板的占用率会一路偏高，最后明明空着的库位也报「容量不足」。
             *
             * decreaseUsedCapacity 不抛异常：已用容量只是派生计数，
             * 万一它自己已经算错了（比如这次修复之前漏减的历史数据），
             * 也不该把发货这个正经业务卡死 —— 它返回 false 并打 warn 日志。
             *
             * 注意这一步和上面的 applyChange 在同一个事务里：
             * 后面 markShipped 失败时，库存和库位占用会一起回滚。
             */
            locationService.decreaseUsedCapacity(allocate.getLocationId(), qty);
        }

        int affected = orderMapper.markShipped(orderId, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        log.info("出库单发货完成 orderNo={} 核销 {} 条分配", order.getOrderNo(), allocates.size());
    }

    @Override
    public List<Long> listTimeoutAllocatedOrderIds(int timeoutMinutes, int limit) {
        if (timeoutMinutes <= 0 || limit <= 0) {
            return List.of();
        }
        // 分配时间早于「现在 - 阈值」的才算超时
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return orderMapper.selectTimeoutAllocatedIds(deadline, limit);
    }

    /* ============================ 拣货完成回调 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPickCompleted(Long orderId, Long allocateId, int qty) {
        if (qty <= 0) {
            return;
        }

        // ① 分配明细 LOCKED → PICKED（CAS，防止同一个分配被拣两次）
        int affected = allocateMapper.markPicked(allocateId, qty);
        if (affected == 0) {
            log.warn("分配记录状态不允许拣货 allocateId={}", allocateId);
            throw new BizException(OutboundErrorCode.ALLOCATE_STATUS_NOT_ALLOWED);
        }

        OutboundAllocate allocate = allocateMapper.selectById(allocateId);

        // ② 回写单据明细的已拣数量
        int itemAffected = itemMapper.increasePickedQty(allocate.getOrderItemId(), qty);
        if (itemAffected == 0) {
            // 拣货数超过分配数了，说明上层校验有漏
            throw new BizException(OutboundErrorCode.SHIP_QTY_MISMATCH);
        }

        // ③ 没有 LOCKED 的了说明整单拣完 → PICKED
        List<OutboundAllocate> stillLocked =
                allocateMapper.selectByOrderIdAndStatus(orderId, AllocateStatus.LOCKED.name());
        if (stillLocked.isEmpty()) {
            List<OutboundOrderItem> items = itemMapper.selectByOrderId(orderId);
            int totalPicked = items.stream().mapToInt(item -> nz(item.getPickedQty())).sum();

            int orderAffected = orderMapper.markPicked(orderId, totalPicked, UserContext.getUserId());
            if (orderAffected > 0) {
                log.info("出库单全部拣完，状态推进到 PICKED orderId={} 已拣总量={}", orderId, totalPicked);
            }
        }
    }

    /* ============================ 给 task 模块用的协作方法 ============================ */

    @Override
    public List<OutboundAllocateVO> listPickableAllocates(Long orderId) {
        return allocateMapper.selectVOByOrderId(orderId).stream()
                .filter(vo -> AllocateStatus.LOCKED.name().equals(vo.getStatus()))
                .toList();
    }

    @Override
    public void markPicking(Long orderId) {
        int affected = orderMapper.markPicking(orderId, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }
        log.info("出库单开始拣货，状态推进到 PICKING orderId={}", orderId);
    }

    /* ============================ 工具 ============================ */

    private OutboundOrderItemVO toItemVO(OutboundOrderItem item) {
        OutboundOrderItemVO vo = new OutboundOrderItemVO();
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

    /* ============================ 红冲（BR-011） ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reverse(Long originalOrderId) {
        OutboundOrder original = orderMapper.selectForUpdate(originalOrderId);
        if (original == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        // 只有已发货的能红冲。草稿/已取消的没有「冲」的意义，已红冲的不能再冲一次
        if (!OutboundStatus.of(original.getStatus()).canReverse()) {
            log.warn("单据状态不允许红冲 orderNo={} status={}", original.getOrderNo(), original.getStatus());
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }
        if (original.getReversedById() != null) {
            log.warn("单据已被红冲 orderNo={} reversedById={}", original.getOrderNo(), original.getReversedById());
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        /*
         * ① 建红冲单。
         * 红冲单不是「新的一单货」，而是原单的镜像：它记录「这批货退回来了」。
         * 用 INSERT ... SELECT 复制，明细单独复制一次。
         */
        String reverseOrderNo = sequenceService.nextNo(BIZ_TYPE, ORDER_NO_PREFIX);
        int copied = orderMapper.copyAsReverseOrder(reverseOrderNo, originalOrderId, UserContext.getUserId());
        if (copied == 0) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        // INSERT ... SELECT 不回传自增主键，所以按单号把新单查回来（单号上有唯一索引）
        OutboundOrder reverseOrder = orderMapper.selectOne(Wrappers.<OutboundOrder>lambdaQuery()
                .eq(OutboundOrder::getOrderNo, reverseOrderNo));
        if (reverseOrder == null) {
            throw new BizException(CommonErrorCode.SYSTEM_ERROR);
        }
        orderMapper.copyItemsAsReverse(reverseOrder.getId(), originalOrderId);

        /*
         * ② 把货退回来。
         * 发货时做的是 qty -n / locked -n，红冲只加回 qty —— 不再动 locked：
         * 那笔锁定在发货时已经核销掉了，红冲只是把实物数量加回来。
         * 每一行都走 applyChange（库存唯一写入口），所以锁、CAS、流水一样都不少。
         */
        List<OutboundAllocate> allocates =
                allocateMapper.selectByOrderIdAndStatus(originalOrderId, AllocateStatus.PICKED.name());
        if (allocates.isEmpty()) {
            // 理论上不可能：发货过的单据一定有 PICKED 分配。真出现说明数据被改过
            log.warn("红冲时找不到已拣货的分配记录 orderNo={}", original.getOrderNo());
            throw new BizException(OutboundErrorCode.NO_ALLOCATION_TO_PICK);
        }

        for (OutboundAllocate allocate : allocates) {
            int qty = nz(allocate.getAllocatedQty());
            inventoryService.applyChange(InventoryChangeCommand.builder()
                    .warehouseId(allocate.getWarehouseId())
                    .locationId(allocate.getLocationId())
                    .skuId(allocate.getSkuId())
                    .batchNo(allocate.getBatchNo())
                    .qtyChange(qty)
                    .txType(InventoryTxType.OUTBOUND_REVERSE)
                    .sourceType(SOURCE_OUTBOUND)
                    .sourceId(reverseOrder.getId())
                    .sourceNo(reverseOrderNo)
                    .remark("出库红冲：" + original.getOrderNo())
                    .createIfAbsent(false)
                    .build());
        }

        /*
         * ③ 标记原单已被红冲。CAS 带 status='SHIPPED'：
         * 并发两个红冲请求只有一个能改成功，另一个在这里被挡掉
         * （uk_reverse_of 唯一索引是第二道防线）。
         */
        int reversed = orderMapper.markReversed(originalOrderId, reverseOrder.getId(), UserContext.getUserId());
        if (reversed == 0) {
            log.warn("标记红冲失败（状态已变）orderNo={}", original.getOrderNo());
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        log.info("出库红冲完成 原单={} 红冲单={} 退回 {} 条分配",
                original.getOrderNo(), reverseOrderNo, allocates.size());
        return reverseOrder.getId();
    }
}
