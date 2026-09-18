package com.myproject.wmsserver.task.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.service.StockTaskService;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 作业任务接口（当前只有上架，拣货任务复用同一组接口） */
@RestController
@RequestMapping("/api/v1/task/stock-tasks")
@RequiredArgsConstructor
public class StockTaskController {

    private final StockTaskService stockTaskService;

    @GetMapping
    public Result<PageResult<StockTaskVO>> page(@Validated StockTaskQuery query) {
        return Result.success(stockTaskService.page(query));
    }

    @GetMapping("/{id}")
    public Result<StockTaskVO> get(@PathVariable Long id) {
        return Result.success(stockTaskService.get(id));
    }

    /** 领取任务：并发下只有一个人能领到，其他人返回 70003 */
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id) {
        stockTaskService.assign(id);
        return Result.success();
    }

    /** 开始作业 */
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        stockTaskService.start(id);
        return Result.success();
    }

    /** 完成上架 —— 这一步会写库存和流水 */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id,
                                 @Valid @RequestBody StockTaskCompleteDTO dto) {
        stockTaskService.complete(id, dto);
        return Result.success();
    }
}