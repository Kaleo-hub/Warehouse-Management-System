package com.myproject.wmsserver.inventory.controller;

import com.myproject.wmsserver.common.idempotent.Idempotent;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inventory.dto.StocktakingCountDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderQuery;
import com.myproject.wmsserver.inventory.service.StocktakingOrderService;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 盘点单接口。 */
@RestController
@RequestMapping("/api/v1/inventory/stocktaking-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class StocktakingOrderController {

    private final StocktakingOrderService stocktakingOrderService;

    @GetMapping
    public Result<PageResult<StocktakingOrderVO>> page(@Validated StocktakingOrderQuery query) {
        return Result.success(stocktakingOrderService.page(query));
    }

    @Idempotent(bizType = "STOCKTAKING_CREATE")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody StocktakingOrderCreateDTO dto) {
        return Result.success(stocktakingOrderService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<StocktakingOrderVO> get(@PathVariable Long id) {
        return Result.success(stocktakingOrderService.get(id));
    }

    @Idempotent(bizType = "STOCKTAKING_COUNT")
    @PutMapping("/{id}/counts")
    public Result<Void> saveCounts(@PathVariable Long id,
                                   @Valid @RequestBody StocktakingCountDTO dto) {
        stocktakingOrderService.saveCounts(id, dto);
        return Result.success();
    }

    @Idempotent(bizType = "STOCKTAKING_POST")
    @PostMapping("/{id}/post")
    public Result<Void> post(@PathVariable Long id) {
        stocktakingOrderService.post(id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        stocktakingOrderService.cancel(id);
        return Result.success();
    }
}