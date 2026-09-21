package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.SummaryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;

import java.util.List;

/**
 * 库存查询（只读）。
 *
 * 【为什么和 InventoryService 分开】
 * InventoryService 上只有 applyChange 一个方法，那个「唯一写入口」的约束是安全边界，
 * 往里面塞查询方法会让这条边界变得模糊 —— 以后别人看到 InventoryService 有十个方法，
 * 就很容易顺手加一个 updateXxx。
 * 查询单独放这里，边界清晰：想改库存？只能走 applyChange。
 */
public interface InventoryQueryService {

    /** 库存分页：keyword 匹配 skuCode/skuName/locationCode */
    PageResult<InventoryVO> pageInventory(InventoryQuery query);

    /** 按 id 查一条库存，不存在抛 10003。移库 / 盘点页面拿它做深链进来时的初值 */
    InventoryVO getInventory(Long id);

    /** 流水分页：keyword 匹配 txNo/skuCode/skuName */
    PageResult<InventoryTransactionVO> pageTransaction(TransactionQuery query);

    /**
     * 按 FIFO 列出某个仓库某个 SKU 的可分配批次（BR-004）。
     *
     * 给出库分配用，不是给 API 输出的 —— 所以直接返回实体，没有再包一层 VO。
     * 返回结果【没有加锁】，调用方必须通过 InventoryService.applyChange 去真正锁定。
     */
    List<Inventory> listFifoAvailable(Long warehouseId, Long skuId);

    /**
     * 库存总览（工作台看板）。可选只看某个仓库。
     * 一次把库存聚合、库位容量、待处理数、按仓库分布都拼好。
     */
    InventorySummaryVO summary(Long warehouseId);

    /**
     * 分组合并汇总。dimension 取 WAREHOUSE / ZONE / SKU，非白名单值按 WAREHOUSE 处理。
     * 用 switch 做白名单，不把前端传的字符串拼进 SQL。
     */
    List<InventoryGroupVO> groupedSummary(SummaryQuery query);

    /** 三类预警：临期 / 冻结 / 库位超容 */
    InventoryAlertVO alerts(SummaryQuery query);
}