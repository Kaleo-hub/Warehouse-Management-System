package com.myproject.wmsserver.inventory.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 对账结果。
 *
 * ========== 对账到底在干什么 ==========
 * inventory.qty 是「余额快照」，inventory_transaction 是「流水账本」。
 * 同一件事被记了两遍，就一定会有对不上的时候：程序 bug、手工改库、宕机丢写……
 * 所以需要一个能自己发现「账不平」的机制，这就是对账。
 *
 * 谁说了算？【以流水为准】（BR-009）。理由：
 *   1. 流水只增不改不删，是事实记录；余额是可以重新推出来的缓存
 *   2. 缓存和它的来源不一致时，永远相信来源
 *   3. 余额可以被静默改掉，流水改不了，所以流水更难撒谎
 *
 * autoFix = true 时按流水把余额重算回来。
 *
 * ========== 一个反直觉的结论：修正时不能写库存流水 ==========
 * 直觉上「改了库存就该留一条流水」，我一开始也是这么写的，
 * 直到测试跑出来它不收敛：
 *
 *   流水 SUM = 150，余额被改坏成 999
 *   走 applyChange 修正 → 写一条 qty_change = -849 的流水
 *   → 余额变成 150（对了），但流水 SUM 变成 -699（又对不上了）
 *   → 下一次对账继续报不平，再修又差 849 …… 永远平不了
 *
 * 根因是语义搞混了：applyChange 记的是「库存变动」（有货进出仓库），
 * 对账修正记的是「把一个派生值重算了一遍」—— 没有任何货进出仓库。
 * 所以正确的做法是修数据 + 留日志（更正式的话写 audit_log），
 * 而不是往账本上写一笔假的变动。账本只记真实发生过的库存变动。
 *
 * 【面试可以这么讲】
 * 「我的库存表是派生数据，流水表才是真相。所以我没有做双写一致性补偿，
 * 而是做了一个可重复执行的核对 + 修正任务：任何时刻跑一次，都能知道账平不平，
 * 不平就按流水把余额拉回来。这里有个坑我踩过 —— 修正动作本身不能写库存流水，
 * 否则账本的 SUM 就被修正动作改动了，对账永远收敛不了。所以修正只改派生数据、
 * 只留日志，账本保持干净。这就是『可对账』比『强一致』更实用的地方：
 * 它不需要所有写路径都 100% 正确，只需要错误能被发现和纠正。」
 */
@Data
public class ReconcileVO {

    /** 扫描了多少条库存行 */
    private Integer checkedCount;

    /** 有多少条不一致 */
    private Integer mismatchCount;

    /** 成功修正了多少条（autoFix = false 时恒为 0） */
    private Integer fixedCount;

    /** 本次是否执行了自动修正 */
    private Boolean autoFix;

    private List<Mismatch> mismatches = new ArrayList<>();

    /** 一条对不上的库存 */
    @Data
    public static class Mismatch {

        private Long inventoryId;

        /** 修正时需要用它拼 applyChange 的入参 */
        private Long warehouseId;

        private Long locationId;

        private String locationCode;

        private Long skuId;

        private String skuCode;

        private String skuName;

        private String batchNo;

        /** 修正时拿来做 CAS：读数之后有人改过这一行，就放弃这次修正，不覆盖别人的改动 */
        private Integer version;

        /** 余额上的在库数量（inventory.qty） */
        private Integer balanceQty;

        /** 按流水 SUM 出来的在库数量 */
        private Integer calculatedQty;

        private Integer balanceLockedQty;

        private Integer calculatedLockedQty;

        private Integer balanceFrozenQty;

        private Integer calculatedFrozenQty;

        /** 修正结果：SKIPPED（只检查）/ FIXED（已按流水修正）/ FAILED（修不动，要人工看） */
        private String fixResult;

        private String remark;
    }
}