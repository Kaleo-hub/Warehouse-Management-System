package com.myproject.wmsserver.inventory.controller;

import com.myproject.wmsserver.common.idempotent.Idempotent;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.inventory.dto.FreezeDTO;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.StocktakingDTO;
import com.myproject.wmsserver.inventory.dto.SummaryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.dto.TransferDTO;
import com.myproject.wmsserver.inventory.service.InventoryAdjustService;
import com.myproject.wmsserver.inventory.service.InventoryExportService;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import com.myproject.wmsserver.inventory.vo.ReconcileVO;
import com.myproject.wmsserver.inventory.vo.StocktakingResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存接口。
 *
 * 【这里没有「直接改库存」的接口，只有「业务动作」】
 * 你找不到 PUT /inventories/{id} 这种接口，是刻意的：
 * 库存不是一个可以随手编辑的字段，它只能由业务动作间接改变。
 * 所以写入接口全都写成动作型 URL（docs/02 第 4.1 节）：
 *
 *   POST /inventories/{id}/freeze    冻结
 *   POST /inventories/{id}/unfreeze  解冻
 *   POST /transfers                  移库
 *   POST /stocktakings               盘点
 *   POST /reconcile                  对账
 *
 * 这么设计有两个好处：
 *   1. 每个动作背后都有明确业务语义，权限可以按动作拆 —— 已经落地：
 *      下面五个写接口都要求 ADMIN / MANAGER，作业员操作不了（会拿 20003）
 *   2. 想改库存就必须说清楚「是什么业务原因」—— 做不到无理由改数，BR-008 才守得住
 *
 * 控制层只做「接收 → 委托 → 返回」，不写业务逻辑、不 try-catch（交给 GlobalExceptionHandler）。
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryAdjustService inventoryAdjustService;
    private final InventoryExportService inventoryExportService;

    /* ==================== 查询（只读） ==================== */

    /** 库存分页 */
    @GetMapping("/inventories")
    public Result<PageResult<InventoryVO>> pageInventory(@Validated InventoryQuery query) {
        return Result.success(inventoryQueryService.pageInventory(query));
    }

    /** 库存详情（移库 / 盘点页面的深链入口用） */
    @GetMapping("/inventories/{id}")
    public Result<InventoryVO> getInventory(@PathVariable Long id) {
        return Result.success(inventoryQueryService.getInventory(id));
    }

    /**
     * 导出库存 Excel。
     *
     * 【为什么是 GET 而不是 POST】导出是只读的，筛选条件和列表页一样走 query string，
     * 这样前端可以直接复用查询条件，浏览器也能正常处理下载。
     * 单次最多 5000 行（见 InventoryExportServiceImpl），超了要另想办法（异步生成文件）。
     */
    @GetMapping("/inventories/export")
    public void exportInventory(@Validated InventoryQuery query, HttpServletResponse response)
            throws IOException {
        prepareExcelResponse(response, "库存");
        inventoryExportService.exportInventory(query, response.getOutputStream());
    }

    /** 导出库存流水 Excel */
    @GetMapping("/transactions/export")
    public void exportTransactions(@Validated TransactionQuery query, HttpServletResponse response)
            throws IOException {
        prepareExcelResponse(response, "库存流水");
        inventoryExportService.exportTransactions(query, response.getOutputStream());
    }

    /**
     * 设置下载响应头。
     *
     * 【文件名必须 URL 编码】中文文件名直接塞进 Content-Disposition 会被
     * 某些浏览器截断或乱码；按 RFC 5987 用 filename*=UTF-8'' 的形式给。
     *
     * 【为什么不用 POST + 返回文件流】GET 才能让浏览器的「下载」语义正常工作
     * （进度、另存为、断点），POST 下载得靠前端自己拼 blob，失败时的错误提示也更难做。
     */
    private void prepareExcelResponse(HttpServletResponse response, String name) throws IOException {
        String fileName = name + "_" + LocalDate.now() + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
        // 让前端能读到文件名（跨域时才需要，同源其实读不到也无所谓，先给上）
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
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

    /* ==================== 动作型写入 ==================== */

    /**
     * 冻结：frozen_qty +qty。
     * 货还在库里，但可用数量变少；冻结的库存不能被分配、不能被移库（BR-005）。
     */
    @Idempotent(bizType = "INVENTORY_FREEZE", key = "#id", ttlMinutes = 10)
    @PostMapping("/inventories/{id}/freeze")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> freeze(@PathVariable Long id, @Valid @RequestBody FreezeDTO dto) {
        inventoryAdjustService.freeze(id, dto);
        return Result.success();
    }

    /** 解冻：frozen_qty -qty，可用数量恢复 */
    @Idempotent(bizType = "INVENTORY_UNFREEZE", key = "#id", ttlMinutes = 10)
    @PostMapping("/inventories/{id}/unfreeze")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> unfreeze(@PathVariable Long id, @Valid @RequestBody FreezeDTO dto) {
        inventoryAdjustService.unfreeze(id, dto);
        return Result.success();
    }

    /**
     * 移库：整批或部分挪到另一个库位。
     * 转出转入在一个事务里，失败两边一起回滚。
     */
    @Idempotent(bizType = "INVENTORY_TRANSFER", ttlMinutes = 10)
    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> transfer(@Valid @RequestBody TransferDTO dto) {
        inventoryAdjustService.transfer(dto);
        return Result.success();
    }

    /**
     * 盘点：传实盘数量，后端算差异并写盘盈 / 盘亏流水。
     * data 里返回系统数量、实盘数量、差异和是否产生了变动。
     */
    @Idempotent(bizType = "INVENTORY_STOCKTAKE", ttlMinutes = 10)
    @PostMapping("/stocktakings")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<StocktakingResultVO> stocktake(@Valid @RequestBody StocktakingDTO dto) {
        return Result.success(inventoryAdjustService.stocktake(dto));
    }

    /**
     * 对账：按流水重算库存余额（BR-009）。
     * autoFix 默认 false，只检查不改数；传 true 才按流水重算余额（这是修派生数据，不写库存流水）。
     */
    @Idempotent(bizType = "INVENTORY_RECONCILE", ttlMinutes = 10)
    @PostMapping("/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<ReconcileVO> reconcile(
            @RequestParam(name = "autoFix", defaultValue = "false") boolean autoFix) {
        return Result.success(inventoryAdjustService.reconcile(autoFix));
    }
}