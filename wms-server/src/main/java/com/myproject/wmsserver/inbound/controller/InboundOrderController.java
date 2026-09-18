package com.myproject.wmsserver.inbound.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inbound.dto.InboundOrderQuery;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.service.InboundOrderService;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
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
 * 入库单接口。
 * 只做「接收 → 委托 → 返回」，不写业务逻辑、不 try-catch（交给 GlobalExceptionHandler）。
 *
 * 动作型接口统一 /{id}/{action}，见 docs/02 第 4.1 节。
 */
@RestController
@RequestMapping("/api/v1/inbound/inbound-orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;

    /**
     * 生成上架任务这个动作，URL 挂在入库单下面（语义是"给这张单派活"），
     * 但真正干活的实现属于 task 模块，所以这里直接委托 StockTaskService。
     *
     * 这样依赖方向只有 task → inbound 一条，
     * 如果让 InboundOrderService 反过来调 StockTaskService，两个 Service 会互相注入形成循环依赖。
     */
    private final StockTaskService stockTaskService;

    /** 分页查询 */
    @GetMapping
    public Result<PageResult<InboundOrderVO>> page(@Validated InboundOrderQuery query) {
        return Result.success(inboundOrderService.page(query));
    }

    /** 创建入库单，data 返回新单据 id */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InboundOrderSaveDTO dto) {
        return Result.success(inboundOrderService.create(dto));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public Result<InboundOrderVO> get(@PathVariable Long id) {
        return Result.success(inboundOrderService.get(id));
    }

    /** 收货登记：整单一次提交。注意这一步不增加库存 */
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id,
                                @Valid @RequestBody InboundReceiveDTO dto) {
        inboundOrderService.receive(id, dto);
        return Result.success();
    }

    /** 取消入库单 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        inboundOrderService.cancel(id);
        return Result.success();
    }

    /** 按已收货明细生成上架任务，data 返回本次生成的任务数 */
    @PostMapping("/{id}/putaway-tasks")
    public Result<Integer> generatePutawayTasks(@PathVariable Long id) {
        return Result.success(stockTaskService.generatePutawayTasks(id));
    }
}