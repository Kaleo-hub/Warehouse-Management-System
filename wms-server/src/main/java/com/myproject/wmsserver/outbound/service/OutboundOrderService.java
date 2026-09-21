package com.myproject.wmsserver.outbound.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.vo.OutboundAllocateVO;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;

import java.util.List;

public interface OutboundOrderService {

    PageResult<OutboundOrderVO> page(OutboundOrderQuery query);

    /** 详情（含明细 + 分配明细） */
    OutboundOrderVO get(Long id);

    /** 创建出库单，返回新 id */
    Long create(OutboundOrderSaveDTO dto);

    /**
     * 分配库存（FIFO，BR-004）。CREATED → ALLOCATED。
     *
     * 三步库存模型的第一步：只锁 locked_qty，qty 不变 —— 货还在货架上。
     * 任一明细的可用库存不够，整单回滚（整单原子）。
     */
    void allocate(Long orderId);

    /** 释放分配：ALLOCATED → CREATED，把 locked_qty 还回去 */
    void release(Long orderId);

    /** 取消：只有 CREATED 可以 */
    void cancel(Long orderId);

    /**
     * 发货：PICKED → SHIPPED。
     *
     * 三步库存模型的最后一步：qty 和 locked_qty 一起减（账实核销）。
     * 拣货不改库存，把不可逆的扣账推到最后 —— 这样拣货失败也不用写补偿。
     */
    void ship(Long orderId);

    /**
     * 红冲（BR-011）：已发货的单据不可撤销，只能开反向单。
     *
     * 会做三件事：建一张红冲单、把货按原路退回来（库存 +qty 并写 OUTBOUND_REVERSE 流水）、
     * 把原单标记为 REVERSED。整个过程一个事务，不会出现「单建了货没回来」。
     *
     * @return 新建的红冲单 id
     */
    Long reverse(Long originalOrderId);

    /**
     * 拣货完成回调（由 StockTaskService 调用）。
     * 累加分配合明细的已拣数量；整单全部拣完则把单据推进到 PICKED。
     */
    void onPickCompleted(Long orderId, Long allocateId, int qty);

    /**
     * 列出待拣货的分配记录（LOCKED），供 task 模块生成拣货任务。
     *
     * 返回出参对象而不是实体：跨模块传递时不要把本模块的表结构暴露出去，
     * 以后 outbound_allocate 改字段也不会把别的模块带崩。
     */
    List<OutboundAllocateVO> listPickableAllocates(Long orderId);

    /**
     * 生成拣货任务后把单据推进到 PICKING（ALLOCATED → PICKING）。
     * 同样由 task 模块调用 —— 单据的状态流转必须由出库模块自己负责。
     */
    void markPicking(Long orderId);

    /**
     * 查出「分配后超过 timeoutMinutes 分钟还没往下走」的单据 id（BR-010）。
     *
     * 出这个只读方法是为了让定时任务能拿到待处理清单，
     * 但【释放动作】仍然走本接口的 release()，状态流转始终归出库模块自己管。
     */
    List<Long> listTimeoutAllocatedOrderIds(int timeoutMinutes, int limit);
}