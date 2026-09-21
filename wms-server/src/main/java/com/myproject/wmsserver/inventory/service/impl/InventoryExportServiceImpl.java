package com.myproject.wmsserver.inventory.service.impl;

import com.alibaba.excel.EasyExcel;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.dto.export.InventoryExportVO;
import com.myproject.wmsserver.inventory.dto.export.TransactionExportVO;
import com.myproject.wmsserver.inventory.service.InventoryExportService;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

/**
 * 导出实现。
 *
 * ========== 为什么直接复用分页查询，而不是另写一套「查全部」的 SQL ==========
 * 导出的筛选条件和列表页必须完全一致（不然用户会觉得「我筛了 A，导出来的是 B」）。
 * 复用同一个 Mapper 方法，条件就天然一致；代价是要给导出一个行数上限。
 *
 * 【为什么必须有上限】一次导出几十万行会：
 *   1. 把服务端内存打爆（EasyExcel 虽然支持分批写，但这里是先查出来再写）
 *   2. 让用户对着一个转圈十分钟的页面发呆
 * 真需要全量导出时，正确做法是「异步任务 + 生成文件 + 通知下载」，不是把超时时间调大。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryExportServiceImpl implements InventoryExportService {

    /** 单次导出的行数上限。够覆盖日常「导出来做个表」，又不会把内存打爆 */
    private static final int EXPORT_LIMIT = 5000;

    private static final String INVENTORY_SHEET = "库存";
    private static final String TRANSACTION_SHEET = "库存流水";

    private final InventoryQueryService inventoryQueryService;

    @Override
    public void exportInventory(InventoryQuery query, OutputStream outputStream) {
        // 借用分页查询：把 size 顶到上限（覆盖掉前端传来的分页大小）
        query.setPage(1);
        query.setSize(EXPORT_LIMIT);
        List<InventoryExportVO> rows = inventoryQueryService.pageInventory(query)
                .getRecords().stream().map(this::toExportRow).toList();

        EasyExcel.write(outputStream, InventoryExportVO.class).sheet(INVENTORY_SHEET).doWrite(rows);
        log.info("导出库存 Excel {} 行", rows.size());
    }

    @Override
    public void exportTransactions(TransactionQuery query, OutputStream outputStream) {
        query.setPage(1);
        query.setSize(EXPORT_LIMIT);
        List<TransactionExportVO> rows = inventoryQueryService.pageTransaction(query)
                .getRecords().stream().map(this::toExportRow).toList();

        EasyExcel.write(outputStream, TransactionExportVO.class).sheet(TRANSACTION_SHEET).doWrite(rows);
        log.info("导出库存流水 Excel {} 行", rows.size());
    }

    private InventoryExportVO toExportRow(com.myproject.wmsserver.inventory.vo.InventoryVO vo) {
        InventoryExportVO row = new InventoryExportVO();
        row.setWarehouseName(vo.getWarehouseName());
        row.setLocationCode(vo.getLocationCode());
        row.setSkuCode(vo.getSkuCode());
        row.setSkuName(vo.getSkuName());
        // 无批次在库里存的是空串，导到 Excel 里显示成空单元格更自然
        row.setBatchNo(vo.getBatchNo() == null || vo.getBatchNo().isEmpty() ? "" : vo.getBatchNo());
        row.setQty(vo.getQty());
        row.setLockedQty(vo.getLockedQty());
        row.setFrozenQty(vo.getFrozenQty());
        row.setAvailableQty(vo.getAvailableQty());
        row.setProductionDate(vo.getProductionDate());
        row.setExpiryDate(vo.getExpiryDate());
        row.setInboundTime(vo.getInboundTime());
        return row;
    }

    private TransactionExportVO toExportRow(com.myproject.wmsserver.inventory.vo.InventoryTransactionVO vo) {
        TransactionExportVO row = new TransactionExportVO();
        row.setTxNo(vo.getTxNo());
        row.setCreateTime(vo.getCreateTime());
        row.setTxType(vo.getTxType());
        row.setWarehouseName(vo.getWarehouseName());
        row.setLocationCode(vo.getLocationCode());
        row.setSkuCode(vo.getSkuCode());
        row.setSkuName(vo.getSkuName());
        row.setBatchNo(vo.getBatchNo() == null || vo.getBatchNo().isEmpty() ? "" : vo.getBatchNo());
        row.setQtyChange(vo.getQtyChange());
        row.setLockedChange(vo.getLockedChange());
        row.setFrozenChange(vo.getFrozenChange());
        row.setQtyAfter(vo.getQtyAfter());
        row.setSourceNo(vo.getSourceNo());
        row.setOperatorName(vo.getOperatorName());
        row.setRemark(vo.getRemark());
        return row;
    }
}