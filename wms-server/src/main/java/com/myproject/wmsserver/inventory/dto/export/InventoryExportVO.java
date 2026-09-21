package com.myproject.wmsserver.inventory.dto.export;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存导出行。
 *
 * 【为什么单独定义导出的 VO，而不是直接导出 InventoryVO】
 * 导出是给人看的，不是给程序看的：
 *   - 列名要是中文（@ExcelProperty）
 *   - 要控制列的顺序和宽度（列一多在 Excel 里就没法看）
 *   - 查询用的 VO 里那些 id 字段对看表的人没有任何意义
 * 混用一个类的结果就是「接口改一下，导出的表也跟着变」，两边互相牵制。
 */
@Data
@ColumnWidth(16)
public class InventoryExportVO {

    @ExcelProperty("仓库")
    private String warehouseName;

    @ExcelProperty("库位")
    private String locationCode;

    @ExcelProperty("SKU 编码")
    @ColumnWidth(18)
    private String skuCode;

    @ExcelProperty("商品名称")
    @ColumnWidth(24)
    private String skuName;

    @ExcelProperty("批次号")
    private String batchNo;

    @ExcelProperty("在库")
    private Integer qty;

    @ExcelProperty("锁定")
    private Integer lockedQty;

    @ExcelProperty("冻结")
    private Integer frozenQty;

    @ExcelProperty("可用")
    private Integer availableQty;

    @ExcelProperty("生产日期")
    private LocalDate productionDate;

    @ExcelProperty("到期日期")
    private LocalDate expiryDate;

    /** EasyExcel 支持 LocalDateTime，会按 yyyy-MM-dd HH:mm:ss 写出 */
    @ExcelProperty("入库时间")
    @ColumnWidth(20)
    private LocalDateTime inboundTime;
}