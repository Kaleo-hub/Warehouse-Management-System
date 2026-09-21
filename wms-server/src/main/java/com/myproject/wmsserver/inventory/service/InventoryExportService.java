package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;

import java.io.OutputStream;

/**
 * 库存 / 流水的 Excel 导出。
 *
 * 【为什么不返回字节数组而是直接写流】
 * 导出几千行的时候，把整个 xlsx 先攒在内存里再返回，等于白白多占一份内存；
 * 直接从流里写出去，浏览器边收边落盘。
 */
public interface InventoryExportService {

    /** 导出库存（按当前筛选条件，最多 EXPORT_LIMIT 行） */
    void exportInventory(InventoryQuery query, OutputStream outputStream);

    /** 导出库存流水 */
    void exportTransactions(TransactionQuery query, OutputStream outputStream);
}