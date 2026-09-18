package com.myproject.wmsserver.inventory.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存查询接口。
 *
 * 【注意这里没有任何写接口】
 * 库存只能由业务动作间接改变（上架入库、出库发货、冻结、盘点……），
 * 不存在「直接修改库存」的 API。这是刻意的 ——
 * 一旦开了这个口子，BR-008（每次变动必须写流水）就守不住了。
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    /** 库存分页 */
    @GetMapping("/inventories")
    public Result<PageResult<InventoryVO>> pageInventory(@Validated InventoryQuery query) {
        return Result.success(inventoryQueryService.pageInventory(query));
    }

    /** 库存流水分页 */
    @GetMapping("/transactions")
    public Result<PageResult<InventoryTransactionVO>> pageTransaction(
            @Validated TransactionQuery query) {
        return Result.success(inventoryQueryService.pageTransaction(query));
    }
}