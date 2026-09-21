package com.myproject.wmsserver.inventory.dto.export;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/** 库存流水导出行 */
@Data
@ColumnWidth(16)
public class TransactionExportVO {

    @ExcelProperty("流水号")
    @ColumnWidth(24)
    private String txNo;

    @ExcelProperty("时间")
    @ColumnWidth(20)
    private LocalDateTime createTime;

    @ExcelProperty("类型")
    private String txType;

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

    @ExcelProperty("在库变动")
    private Integer qtyChange;

    @ExcelProperty("锁定变动")
    private Integer lockedChange;

    @ExcelProperty("冻结变动")
    private Integer frozenChange;

    @ExcelProperty("变动后")
    private Integer qtyAfter;

    @ExcelProperty("来源单号")
    @ColumnWidth(20)
    private String sourceNo;

    @ExcelProperty("操作人")
    private String operatorName;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}