package com.myproject.wmsserver.outbound.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.dto.OutboundOrderSaveDTO;
import com.myproject.wmsserver.outbound.service.OutboundOrderService;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import com.myproject.wmsserver.task.service.StockTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出库单接口。
 *
 * 【完整业务链路】
 *   建单 → 分配库存(FIFO) → 生成拣货任务 → 领取/拣货 → 发货
 *   CREATED → ALLOCATED   → PICKING        → PICKED   → SHIPPED
 */
@RestController
@RequestMapping("/api/v1/outbound/outbound-orders")
@RequiredArgsConstructor
public class OutboundOrderController {

    private final OutboundOrderService outboundOrderService;

    /**
     * 生成拣货任务的 URL 挂在出库单下面，但实现属于 task 模块。
     * 依赖方向只有 task → outbound 一条，如果让 OutboundOrderService 反过来调
     * StockTaskService，两个 Service 就会互相注入形成循环依赖。
     */
    private final StockTaskService stockTaskService;

    @GetMapping
    public Result<PageResult<OutboundOrderVO>> page(@Validated OutboundOrderQuery query) {
        return Result.success(outboundOrderService.page(query));
    }

    /** 创建出库单，data 返回新单据 id */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OutboundOrderSaveDTO dto) {
        return Result.success(outboundOrderService.create(dto));
    }

    /** 详情（含出库明细 + 分配明细） */
    @GetMapping("/{id}")
    public Result<OutboundOrderVO> get(@PathVariable Long id) {
        return Result.success(outboundOrderService.get(id));
    }

    /**
     * 分配库存（FIFO）。
     * 只锁 locked_qty，qty 不变 —— 货还在货架上。
     * 任一明细可用量不够就整单回滚。
     */
    @PostMapping("/{id}/allocate")
    public Result<Void> allocate(@PathVariable Long id) {
        outboundOrderService.allocate(id);
        return Result.success();
    }

    /** 释放分配：把 locked_qty 还回去，单据回到 CREATED */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        outboundOrderService.release(id);
        return Result.success();
    }

    /** 按分配明细生成拣货任务，单据推进到 PICKING */
    @PostMapping("/{id}/pick-tasks")
    public Result<Integer> generatePickTasks(@PathVariable Long id) {
        return Result.success(stockTaskService.generatePickTasks(id));
    }

    /**
     * 发货：qty 和 locked_qty 一起减（账实核销）。
     * 这是三步库存模型里唯一不可逆的一步。
     */
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        outboundOrderService.ship(id);
        return Result.success();
    }

    /** 取消：只有 CREATED 允许 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        outboundOrderService.cancel(id);
        return Result.success();
    }
}