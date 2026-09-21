package com.myproject.wmsserver.task.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InboundErrorCode;
import com.myproject.wmsserver.common.exception.TaskErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.inbound.enums.InboundStatus;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderItemVO;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;
import com.myproject.wmsserver.inventory.enums.InventoryTxType;
import com.myproject.wmsserver.inventory.service.InventoryService;
import com.myproject.wmsserver.common.exception.OutboundErrorCode;
import com.myproject.wmsserver.outbound.enums.OutboundStatus;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.outbound.vo.OutboundAllocateVO;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.entity.StockTask;
import com.myproject.wmsserver.task.enums.TaskStatus;
import com.myproject.wmsserver.task.enums.TaskType;
import com.myproject.wmsserver.task.mapper.StockTaskMapper;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作业任务服务。
 *
 * 【依赖方向】只有 task → inbound / inventory / basedata 这一个方向。
 * InboundOrderService 不反过来依赖 StockTaskService，所以不会出现循环依赖（Spring 启动会直接报错）。
 * 入库模块那边需要"生成上架任务"时，是由 InboundOrderController 直接调本服务的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTaskServiceImpl implements StockTaskService {

    private static final String SOURCE_INBOUND = "INBOUND_ORDER";
    private static final String SOURCE_OUTBOUND = "OUTBOUND_ORDER";
    private static final String BIZ_TYPE = "STOCK_TASK";
    private static final String TASK_NO_PREFIX = "TASK";

    private final StockTaskMapper taskMapper;
    private final InboundOrderService inboundOrderService;
    private final OutboundOrderService outboundOrderService;
    private final InventoryService inventoryService;
    private final LocationService locationService;
    private final SequenceService sequenceService;

    /* ============================ 查询 ============================ */

    @Override
    public PageResult<StockTaskVO> page(StockTaskQuery query) {
        Page<StockTaskVO> page = new Page<>(query.getPage(), query.getSize());
        taskMapper.selectTaskPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public StockTaskVO get(Long id) {
        StockTaskVO vo = taskMapper.selectTaskDetail(id);
        if (vo == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return vo;
    }

    /* ============================ 生成上架任务 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generatePutawayTasks(Long inboundOrderId) {
        // 通过 InboundOrderService 拿单据（跨模块调 Service，不注入别人的 Mapper）
        InboundOrderVO order = inboundOrderService.get(inboundOrderId);

        if (!InboundStatus.of(order.getStatus()).canGeneratePutawayTasks()) {
            log.warn("入库单状态不允许生成上架任务 orderNo={} status={}",
                    order.getOrderNo(), order.getStatus());
            throw new BizException(InboundErrorCode.STATUS_NOT_ALLOWED);
        }

        // 幂等：找出这张单已
        // 经派出去、还没做完的任务，它们覆盖的明细不再重复生成
        List<StockTask> existing = taskMapper.selectList(Wrappers.<StockTask>lambdaQuery()
                .eq(StockTask::getSourceType, SOURCE_INBOUND)
                .eq(StockTask::getSourceId, inboundOrderId)
                .notIn(StockTask::getStatus, TaskStatus.DONE.name(), TaskStatus.CANCELLED.name()));
        Set<Long> busyItemIds = existing.stream()
                .map(StockTask::getSourceItemId)
                .collect(Collectors.toSet());

        // 收货暂存位作为上架起点
        Long fromLocationId = taskMapper.selectTempLocationId(order.getWarehouseId());

        int created = 0;
        for (InboundOrderItemVO item : order.getItems()) {
            int pending = nz(item.getReceivedQty()) - nz(item.getPutawayQty());
            if (pending <= 0) {
                continue;   // 这一行还没收货 / 已经上架完
            }
            if (busyItemIds.contains(item.getId())) {
                continue;   // 已经派过任务了
            }

            Long toLocationId = taskMapper.selectRecommendedPutawayLocationId(
                    order.getWarehouseId(), item.getSkuId());

            StockTask task = new StockTask();
            task.setTaskNo(sequenceService.nextNo(BIZ_TYPE, TASK_NO_PREFIX));
            task.setTaskType(TaskType.PUTAWAY.name());
            task.setWarehouseId(order.getWarehouseId());
            task.setSourceType(SOURCE_INBOUND);
            task.setSourceId(order.getId());
            task.setSourceNo(order.getOrderNo());
            task.setSourceItemId(item.getId());
            task.setStatus(TaskStatus.CREATED.name());
            task.setPriority(0);
            task.setSkuId(item.getSkuId());
            task.setBatchNo(item.getBatchNo() == null ? "" : item.getBatchNo());
            task.setFromLocationId(fromLocationId);
            task.setToLocationId(toLocationId);
            task.setPlanQty(pending);
            task.setActualQty(0);
            task.setRetryCount(0);
            task.setCreateBy(UserContext.getUserId());
            taskMapper.insert(task);
            created++;
        }

        if (created == 0) {
            // 所有明细要么已上架完、要么已经有在途任务 —— 当作重复提交处理
            log.warn("没有可生成的上架任务 orderNo={}", order.getOrderNo());
            throw new BizException(CommonErrorCode.IDEMPOTENT_CONFLICT);
        }

        log.info("生成上架任务 orderNo={} 共 {} 条", order.getOrderNo(), created);
        return created;
    }

    /* ============================ 生成拣货任务 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generatePickTasks(Long outboundOrderId) {
        OutboundOrderVO order = outboundOrderService.get(outboundOrderId);

        if (!OutboundStatus.of(order.getStatus()).canGeneratePickTasks()) {
            log.warn("出库单状态不允许生成拣货任务 orderNo={} status={}",
                    order.getOrderNo(), order.getStatus());
            throw new BizException(OutboundErrorCode.STATUS_NOT_ALLOWED);
        }

        List<OutboundAllocateVO> allocates = outboundOrderService.listPickableAllocates(outboundOrderId);
        if (allocates.isEmpty()) {
            throw new BizException(OutboundErrorCode.NO_ALLOCATION_TO_PICK);
        }

        // 幂等：已经派过活的分配记录不再重复生成（按 sourceItemId = 分配明细 id 判断）
        List<StockTask> existing = taskMapper.selectList(Wrappers.<StockTask>lambdaQuery()
                .eq(StockTask::getSourceType, SOURCE_OUTBOUND)
                .eq(StockTask::getSourceId, outboundOrderId)
                .eq(StockTask::getTaskType, TaskType.PICK.name())
                .notIn(StockTask::getStatus, TaskStatus.DONE.name(), TaskStatus.CANCELLED.name()));
        Set<Long> busyAllocateIds = existing.stream()
                .map(StockTask::getSourceItemId)
                .collect(Collectors.toSet());

        // 拣出来的货先放暂存位（有发货区的话这里换成发货区库位）
        Long toLocationId = taskMapper.selectTempLocationId(order.getWarehouseId());

        int created = 0;
        for (OutboundAllocateVO allocate : allocates) {
            if (busyAllocateIds.contains(allocate.getId())) {
                continue;
            }

            StockTask task = new StockTask();
            task.setTaskNo(sequenceService.nextNo(BIZ_TYPE, TASK_NO_PREFIX));
            task.setTaskType(TaskType.PICK.name());
            task.setWarehouseId(order.getWarehouseId());
            task.setSourceType(SOURCE_OUTBOUND);
            task.setSourceId(order.getId());
            task.setSourceNo(order.getOrderNo());
            // 拣货任务对应的是"一条分配明细"（= 一个库位的一个批次），不是整条出库明细
            task.setSourceItemId(allocate.getId());
            task.setStatus(TaskStatus.CREATED.name());
            task.setPriority(0);
            task.setSkuId(allocate.getSkuId());
            task.setBatchNo(allocate.getBatchNo() == null ? "" : allocate.getBatchNo());
            task.setFromLocationId(allocate.getLocationId());
            task.setToLocationId(toLocationId);
            task.setPlanQty(allocate.getAllocatedQty());
            task.setActualQty(0);
            task.setRetryCount(0);
            task.setCreateBy(UserContext.getUserId());
            taskMapper.insert(task);
            created++;
        }

        if (created == 0) {
            log.warn("没有可生成的拣货任务 orderNo={}", order.getOrderNo());
            throw new BizException(CommonErrorCode.IDEMPOTENT_CONFLICT);
        }

        // 单据推进到 PICKING（由 outbound 模块自己改自己的状态）
        outboundOrderService.markPicking(outboundOrderId);

        log.info("生成拣货任务 orderNo={} 共 {} 条", order.getOrderNo(), created);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelUnfinishedPickTasks(Long orderId, String reason) {
        if (orderId == null) {
            return 0;
        }
        int affected = taskMapper.cancelUnfinishedTasks(
                SOURCE_OUTBOUND, orderId, TaskType.PICK.name(), reason);
        if (affected > 0) {
            log.info("作废未完成拣货任务 orderId={} 条数={} 原因={}", orderId, affected, reason);
        }
        return affected;
    }

    /* ============================ 领取 / 开始 ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long taskId) {
        UserContext.CurrentUser user = UserContext.get();
        Long assigneeId = user == null ? null : user.getUserId();
        // 注意：JWT 里只有 username，没有 realName。
        // 想显示真实姓名的话，要扩展 UserContext 让它带上 realName。
        String assigneeName = user == null ? "" : user.getUsername();

        int affected = taskMapper.assign(taskId, assigneeId, assigneeName);
        if (affected == 0) {
            // 影响行数 0 有两种可能：任务不存在，或已被别人领走
            if (taskMapper.selectById(taskId) == null) {
                throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
            }
            throw new BizException(TaskErrorCode.TASK_ALREADY_ASSIGNED);
        }
        log.info("任务已领取 taskId={} assignee={}", taskId, assigneeName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long taskId) {
        int affected = taskMapper.start(taskId);
        if (affected == 0) {
            throw new BizException(TaskErrorCode.TASK_STATUS_NOT_ALLOWED);
        }
        log.info("任务开始作业 taskId={}", taskId);
    }

    /* ============================ 完成上架 ★ ============================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long taskId, StockTaskCompleteDTO dto) {
        StockTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        int actualQty = nz(dto.getActualQty());
        if (actualQty > nz(task.getPlanQty())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }

        // ---------- ① 先 CAS 占状态：PROCESSING → DONE ----------
        // 为什么放在最前面？并发的第二个请求会在这里就拿到 0 行、直接失败，
        // 不会继续往下走 —— 失败得快，也不会重复执行后面的业务动作。
        // 就算后面任何一步抛异常，整个事务回滚，这个 CAS 也会一起撤销。
        int affected = taskMapper.complete(taskId, actualQty, dto.getRemark());
        if (affected == 0) {
            log.warn("任务状态不允许完成 taskId={} status={}", taskId, task.getStatus());
            throw new BizException(TaskErrorCode.TASK_STATUS_NOT_ALLOWED);
        }

        // ---------- ② 按任务类型分流 ----------
        if (TaskType.PICK.name().equals(task.getTaskType())) {
            completePick(task, actualQty);
        } else {
            completePutaway(task, actualQty);
        }
    }

    /**
     * 上架完成：写库存 + 占库位容量 + 回写入库单。
     * 这是整个 WMS 唯一一处【增加】库存的地方。
     */
    private void completePutaway(StockTask task, int actualQty) {
        if (task.getToLocationId() == null) {
            throw new BizException(TaskErrorCode.TASK_TARGET_LOCATION_MISSING);
        }

        /*
         * 先把来源入库明细取出来。
         * 目的是拿到生产日期/到期日期 —— 上架时要写进 inventory 表，
         * 「临期预警」和出库 FIFO 的次级排序都要靠它们。
         * 只在首次建库存行时写入（ensureRow 里 ON DUPLICATE KEY 不会覆盖）。
         */
        InboundOrderItemVO sourceItem = null;
        if (SOURCE_INBOUND.equals(task.getSourceType()) && task.getSourceItemId() != null) {
            sourceItem = inboundOrderService.getItem(task.getSourceId(), task.getSourceItemId());
        }

        if (actualQty > 0) {
            inventoryService.applyChange(InventoryChangeCommand.builder()
                    .warehouseId(task.getWarehouseId())
                    .locationId(task.getToLocationId())
                    .skuId(task.getSkuId())
                    .batchNo(task.getBatchNo())
                    .qtyChange(actualQty)
                    .txType(InventoryTxType.PUTAWAY_IN)
                    .sourceType("TASK")
                    .sourceId(task.getId())
                    .sourceNo(task.getTaskNo())
                    .operatorName(task.getAssigneeName())
                    .remark("上架入库")
                    .productionDate(sourceItem == null ? null : sourceItem.getProductionDate())
                    .expiryDate(sourceItem == null ? null : sourceItem.getExpiryDate())
                    .build());

            // 和上一步在同一个事务里：容量不够会把库存一起回滚，
            // 不会出现「货进了、库位超容」
            locationService.increaseUsedCapacity(task.getToLocationId(), actualQty);
        }

        if (sourceItem != null) {
            inboundOrderService.onPutawayCompleted(task.getSourceId(), task.getSourceItemId(), actualQty);
        }

        log.info("上架完成 taskNo={} 数量={} 目标库位={}",
                task.getTaskNo(), actualQty, task.getToLocationId());
    }

    /**
     * 拣货完成：★ 完全不碰库存。
     *
     * 为什么？货虽然已经从货架上拿下来了，但拣货可能失败（破损、找不到货、
     * 复核时发现不对），这时候如果已经把账扣了，就得写一大堆补偿逻辑把它加回去。
     *
     * 所以三步库存模型里，拣货这一步账面上什么都不动：
     *   分配时 locked_qty +n（货还在货架上，只是账上占住）
     *   拣货时都不动        （货在推车上，账面先不动）
     *   发货时 qty、locked 一起 -n（账实核销，不可逆）
     *
     * 把不可逆的操作推到最后 —— 和入库那边「收货不改库存、上架才改」是同一个思路。
     */
    private void completePick(StockTask task, int actualQty) {
        if (SOURCE_OUTBOUND.equals(task.getSourceType()) && task.getSourceItemId() != null) {
            // 回写出库单：分配明细 LOCKED → PICKED，全部拣完单据推进到 PICKED
            outboundOrderService.onPickCompleted(task.getSourceId(), task.getSourceItemId(), actualQty);
        }

        log.info("拣货完成 taskNo={} 数量={} 源库位={}",
                task.getTaskNo(), actualQty, task.getFromLocationId());
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}