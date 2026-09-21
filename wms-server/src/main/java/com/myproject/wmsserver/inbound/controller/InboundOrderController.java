package com.myproject.wmsserver.inbound.controller;

import com.myproject.wmsserver.common.idempotent.Idempotent;
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

    /**
     * 创建入库单，data 返回新单据 id。
     *
     * 【为什么需要幂等】这时候单号还不存在，没有任何「业务键」能把两次请求认成同一件事，
     * 所以靠前端在打开新建页时生成的 X-Idempotent-Key（双击、重试都用同一个）。
     * 不传也不至于出错：切面会退化成「业务键 anon + 请求体哈希」，内容完全相同的重复提交照样挡得住。
     */
    @Idempotent(bizType = "INBOUND_CREATE")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InboundOrderSaveDTO dto) {
        return Result.success(inboundOrderService.create(dto));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public Result<InboundOrderVO> get(@PathVariable Long id) {
        return Result.success(inboundOrderService.get(id));
    }

    /**
     * 收货登记：整单一次提交。注意这一步不增加库存。
     *
     * 【为什么这个接口必须幂等】一张入库单允许分批收货，所以
     * 「状态」挡不住重复（单据还在 RECEIVING），「数量上限」也挡不住
     * （预计 100，收了 30 再收 30 是合法的）。
     * 唯一能识别「这就是刚才那次提交」的，只有业务键 + 请求体哈希。
     * TTL 取 10 分钟：收货是现场高频动作，去重窗口短一点，减少误挡。
     */
    @Idempotent(bizType = "INBOUND_RECEIVE", key = "#id", ttlMinutes = 10)
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