package com.myproject.wmsserver.inventory.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.SummaryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
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

    /* ==================== 看板：总览 / 汇总 / 预警 ==================== */

    /**
     * 库存总览（工作台看板）。
     * 可选参数 warehouseId：传了就只看这个仓库，不传看全局。
     */
    @GetMapping("/summary")
    public Result<InventorySummaryVO> summary(@RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryQueryService.summary(warehouseId));
    }

    /**
     * 分组汇总，维度切换：WAREHOUSE / ZONE / SKU。
     * 返回全量分组（不翻页）—— 这是给"看全貌"用的，几十行以内；真的多到几百行时再加分页。
     */
    @GetMapping("/summary/grouped")
    public Result<List<InventoryGroupVO>> groupedSummary(@Validated SummaryQuery query) {
        return Result.success(inventoryQueryService.groupedSummary(query));
    }

    /** 三类库存预警：临期 / 冻结 / 库位超容 */
    @GetMapping("/alerts")
    public Result<InventoryAlertVO> alerts(@Validated SummaryQuery query) {
        return Result.success(inventoryQueryService.alerts(query));
    }
}