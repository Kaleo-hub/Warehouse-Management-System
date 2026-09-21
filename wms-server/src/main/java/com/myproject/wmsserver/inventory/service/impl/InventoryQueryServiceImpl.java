package com.myproject.wmsserver.inventory.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.SummaryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.mapper.InventoryTransactionMapper;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 库存查询实现。只读，所以没有 @Transactional */
@Service
@RequiredArgsConstructor
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;

    @Override
    public PageResult<InventoryVO> pageInventory(InventoryQuery query) {
        Page<InventoryVO> page = new Page<>(query.getPage(), query.getSize());
        inventoryMapper.selectInventoryPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public PageResult<InventoryTransactionVO> pageTransaction(TransactionQuery query) {
        Page<InventoryTransactionVO> page = new Page<>(query.getPage(), query.getSize());
        transactionMapper.selectTransactionPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public List<Inventory> listFifoAvailable(Long warehouseId, Long skuId) {
        return inventoryMapper.selectFifoAvailable(warehouseId, skuId);
    }

    /* ==================== 看板：总览 / 汇总 / 预警 ==================== */

    @Override
    public InventorySummaryVO summary(Long warehouseId) {
        // 三块数据分别查，再合并成一个 VO。
        // 拆开查比一条十几个标量子查询的大 SQL 好读，也多不了几次往返（看板就用一次）。
        InventorySummaryVO stock = inventoryMapper.selectInventoryAggregate(warehouseId);
        InventorySummaryVO capacity = inventoryMapper.selectLocationCapacityAggregate(warehouseId);
        InventorySummaryVO pending = inventoryMapper.selectPendingCounts();

        InventorySummaryVO vo = new InventorySummaryVO();
        vo.setTotalQty(stock.getTotalQty());
        vo.setLockedQty(stock.getLockedQty());
        vo.setFrozenQty(stock.getFrozenQty());
        vo.setAvailableQty(stock.getAvailableQty());
        vo.setSkuCount(stock.getSkuCount());
        vo.setUsedLocationCount(stock.getUsedLocationCount());

        vo.setLocationCount(capacity.getLocationCount());
        vo.setCapacityTotal(capacity.getCapacityTotal());
        vo.setCapacityUsed(capacity.getCapacityUsed());

        // 占用率：容量合计为 0（全是不限容量的临时位）时给 null，
        // 让前端显示「—」而不是误导性的 0%
        long capTotal = nz(capacity.getCapacityTotal());
        if (capTotal > 0) {
            vo.setCapacityUsageRate(Math.round(nz(capacity.getCapacityUsed()) * 1000.0 / capTotal) / 10.0);
        }

        vo.setPendingTaskCount(pending.getPendingTaskCount());
        vo.setPendingInboundCount(pending.getPendingInboundCount());
        vo.setPendingOutboundCount(pending.getPendingOutboundCount());

        vo.setByWarehouse(inventoryMapper.selectGroupByWarehouse(warehouseId));
        return vo;
    }

    @Override
    public List<InventoryGroupVO> groupedSummary(SummaryQuery query) {
        String dimension = query.getDimension() == null
                ? "WAREHOUSE" : query.getDimension().trim().toUpperCase();
        Long warehouseId = query.getWarehouseId();

        // 白名单分发：前端传什么都不可能拼进 SQL，最多退化成默认维度
        return switch (dimension) {
            case "ZONE" -> inventoryMapper.selectGroupByZone(warehouseId);
            case "SKU" -> inventoryMapper.selectGroupBySku(warehouseId);
            default -> inventoryMapper.selectGroupByWarehouse(warehouseId);
        };
    }

    @Override
    public InventoryAlertVO alerts(SummaryQuery query) {
        // 阈值兜底：前端传了负数或没传就按 30 天算
        int expireDays = (query.getExpireDays() == null || query.getExpireDays() < 0)
                ? 30 : query.getExpireDays();
        Long warehouseId = query.getWarehouseId();

        InventoryAlertVO vo = new InventoryAlertVO();
        vo.setExpireDays(expireDays);
        vo.setExpiring(inventoryMapper.selectExpiringAlerts(expireDays, warehouseId));
        vo.setFrozen(inventoryMapper.selectFrozenAlerts(warehouseId));
        vo.setOverCapacity(inventoryMapper.selectOverCapacityAlerts(warehouseId));
        return vo;
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }
}