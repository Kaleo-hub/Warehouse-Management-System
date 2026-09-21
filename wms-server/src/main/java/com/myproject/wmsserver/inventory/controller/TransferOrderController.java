package com.myproject.wmsserver.inventory.controller;

import com.myproject.wmsserver.common.idempotent.Idempotent;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inventory.dto.TransferOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderQuery;
import com.myproject.wmsserver.inventory.service.TransferOrderService;
import com.myproject.wmsserver.inventory.vo.TransferOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;

/** 移库单接口。 */
@RestController
@RequestMapping("/api/v1/inventory/transfer-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class TransferOrderController {

    private final TransferOrderService transferOrderService;

    @GetMapping
    public Result<PageResult<TransferOrderVO>> page(@Validated TransferOrderQuery query) {
        return Result.success(transferOrderService.page(query));
    }

    @Idempotent(bizType = "TRANSFER_CREATE")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TransferOrderCreateDTO dto) {
        return Result.success(transferOrderService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<TransferOrderVO> get(@PathVariable Long id) {
        return Result.success(transferOrderService.get(id));
    }

    @Idempotent(bizType = "TRANSFER_COMPLETE")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        transferOrderService.complete(id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        transferOrderService.cancel(id);
        return Result.success();
    }
}
