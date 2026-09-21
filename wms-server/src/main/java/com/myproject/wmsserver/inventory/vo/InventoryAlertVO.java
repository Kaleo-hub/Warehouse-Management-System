package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存预警汇总。
 *
 * 三类问题一次返回，前端分三块展示：
 *   1. 临期 / 已过期  —— 该优先发出去或处理
 *   2. 长期冻结      —— 冻了但没处理，占着可用量
 *   3. 库位超容      —— 数据异常（正常流程不应该出现），需要人工核对
 */
@Data
public class InventoryAlertVO {

    private List<ExpiryAlert> expiring;

    private List<FrozenAlert> frozen;

    private List<CapacityAlert> overCapacity;

    /** 临期阈值（天），回显给前端说明"多少天内算临期" */
    private Integer expireDays;

    /* ---------------- 临期 ---------------- */

    @Data
    public static class ExpiryAlert {
        private Long inventoryId;
        private Long warehouseId;
        private String warehouseName;
        private String locationCode;
        private String skuCode;
        private String skuName;
        private String batchNo;
        private Integer qty;
        private LocalDate productionDate;
        private LocalDate expiryDate;
        /** 距到期天数，负数表示已经过期 */
        private Integer daysToExpire;
    }

    /* ---------------- 冻结 ---------------- */

    @Data
    public static class FrozenAlert {
        private Long inventoryId;
        private Long warehouseId;
        private String warehouseName;
        private String locationCode;
        private String skuCode;
        private String skuName;
        private String batchNo;
        private Integer qty;
        private Integer frozenQty;
        private Integer availableQty;
    }

    /* ---------------- 超容 ---------------- */

    @Data
    public static class CapacityAlert {
        private Long locationId;
        private Long warehouseId;
        private String warehouseName;
        private String zoneName;
        private String locationCode;
        private String locationName;
        private Integer capacity;
        private Integer usedCapacity;
        /** 超出多少 */
        private Integer overQty;
    }
}