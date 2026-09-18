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
    private static final String BIZ_TYPE = "STOCK_TASK";
    private static final String TASK_NO_PREFIX = "TASK";

    private final StockTaskMapper taskMapper;
    private final InboundOrderService inboundOrderService;
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

        // 幂等：找出这张单已经派出去、还没做完的任务，它们覆盖的明细不再重复生成
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
        if (task.getToLocationId() == null) {
            throw new BizException(TaskErrorCode.TASK_TARGET_LOCATION_MISSING);
        }

        // ---------- ① 先 CAS 占状态：PROCESSING → DONE ----------
        // 为什么放在最前面？
        // 并发的第二个请求会在这里就拿到 0 行、直接失败，
        // 不会继续往下走去抢库存行锁 —— 失败得快，也不会重复加库存。
        // 就算后面任何一步抛异常，整个事务回滚，这个 CAS 也会一起撤销。
        int affected = taskMapper.complete(taskId, actualQty, dto.getRemark());
        if (affected == 0) {
            log.warn("任务状态不允许完成 taskId={} status={}", taskId, task.getStatus());
            throw new BizException(TaskErrorCode.TASK_STATUS_NOT_ALLOWED);
        }

        // ---------- ② 写库存（唯一入口）----------
        // 这里是整个 WMS 唯一一处增加库存的地方。
        // 锁、版本号、流水全部由 applyChange 负责，这里只描述"发生了什么"。
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
                    .build());

            // ---------- ③ 占用库位容量（BR-006）----------
            // 和 ② 在同一个事务里：容量不够会把库存一起回滚，不会出现"货进了、库位超容"
            locationService.increaseUsedCapacity(task.getToLocationId(), actualQty);
        }

        // ---------- ④ 回写来源单据 ----------
        if (SOURCE_INBOUND.equals(task.getSourceType()) && task.getSourceItemId() != null) {
            inboundOrderService.onPutawayCompleted(task.getSourceId(), task.getSourceItemId(), actualQty);
        }

        log.info("上架完成 taskNo={} 数量={} 目标库位={}",
                task.getTaskNo(), actualQty, task.getToLocationId());
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}