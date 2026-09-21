package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.vo.InventoryAlertVO;
import com.myproject.wmsserver.inventory.vo.InventoryGroupVO;
import com.myproject.wmsserver.inventory.vo.InventorySummaryVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import com.myproject.wmsserver.inventory.vo.ReconcileVO;
import com.myproject.wmsserver.inventory.vo.StocktakingCandidateVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 确保「库位 + SKU + 批次」这一行存在。
     *
     * INSERT ... ON DUPLICATE KEY UPDATE id = id 是个常用技巧：
     *   - 行不存在 → 插入一条全是 0 的库存，inbound_time 记为当前时间（FIFO 要用）
     *   - 行已存在 → 唯一键冲突，走 UPDATE 分支，但 id = id 相当于什么都没改
     *
     * 为什么要有这一步？因为后面要用 SELECT ... FOR UPDATE 锁行，
     * 锁一个不存在的行是锁不住的；先保证行存在，锁才有效。
     *
     * 返回值不用关心（0 或 1）。
     */
    int ensureRow(@Param("warehouseId") Long warehouseId,
                  @Param("locationId") Long locationId,
                  @Param("skuId") Long skuId,
                  @Param("batchNo") String batchNo,
                  @Param("productionDate") LocalDate productionDate,
                  @Param("expiryDate") LocalDate expiryDate);

    /**
     * 按业务唯一键查询并加排他锁（SELECT ... FOR UPDATE）。
     * 必须在事务里调用，否则锁会立刻释放。
     */
    Inventory selectForUpdate(@Param("locationId") Long locationId,
                              @Param("skuId") Long skuId,
                              @Param("batchNo") String batchNo);

    /**
     * 更新三个数量，带 version 乐观锁 CAS。
     * 影响行数为 0 说明 version 对不上 —— 理论上 FOR UPDATE 已经排除了这种情况，
     * 留着它作为第二道防线（也方便以后换成纯乐观锁方案）。
     */
    int updateQty(@Param("id") Long id,
                  @Param("qty") int qty,
                  @Param("lockedQty") int lockedQty,
                  @Param("frozenQty") int frozenQty,
                  @Param("version") int version);

    /** 不带锁的查询，给测试和排查用 */
    Inventory selectByBusinessKey(@Param("locationId") Long locationId,
                                  @Param("skuId") Long skuId,
                                  @Param("batchNo") String batchNo);

    /**
     * 按 FIFO 列出可分配的库存批次（BR-004）。
     *
     * 排序：首次入库时间早的优先，其次生产日期早的，最后用 id 兜底保证顺序稳定。
     * 只返回 available_qty > 0 的行 —— 走 idx_available 索引，不要在这里做函数运算。
     *
     * 【注意】这是普通的读，不加锁。
     * 调用方拿到候选后必须通过 applyChange 去真正锁定，
     * 那个方法会重新校验可用量并在不满足时抛异常 —— 这样才是安全的。
     */
    List<Inventory> selectFifoAvailable(@Param("warehouseId") Long warehouseId,
                                        @Param("skuId") Long skuId);

    /** 库存分页查询，带仓库名 / 库位编码 / 商品编码名称 */
    IPage<InventoryVO> selectInventoryPage(IPage<InventoryVO> page,
                                           @Param("query") InventoryQuery query);

    /**
     * 按 id 查一条库存（移库 / 盘点的深链入口用）。
     * 和分页查询共用同一段列定义，保证返回字段完全一致。
     */
    InventoryVO selectInventoryById(@Param("id") Long id);

    /* ==================== 汇总 / 预警（看板用） ==================== */

    /** 库存侧聚合：总在库/锁定/冻结/可用 + SKU 数 + 有货库位数 */
    InventorySummaryVO selectInventoryAggregate(@Param("warehouseId") Long warehouseId);

    /** 库位容量聚合：启用库位数 + 容量合计 + 已用合计（capacity=0 的不限位不计入合计） */
    InventorySummaryVO selectLocationCapacityAggregate(@Param("warehouseId") Long warehouseId);

    /**
     * 待处理数：任务 / 入库单 / 出库单。
     *
     * 【跨模块只读投影】没有注入别的模块的 Mapper，是本模块自己发一条
     * 带标量子查询的 SELECT，属于「读投影」而不是「写别人的表」。
     * 这样看板一次请求就够，不用调三个 Service 各查一遍。
     */
    InventorySummaryVO selectPendingCounts();

    List<InventoryGroupVO> selectGroupByWarehouse(@Param("warehouseId") Long warehouseId);

    List<InventoryGroupVO> selectGroupByZone(@Param("warehouseId") Long warehouseId);

    List<InventoryGroupVO> selectGroupBySku(@Param("warehouseId") Long warehouseId);

    List<InventoryAlertVO.ExpiryAlert> selectExpiringAlerts(@Param("expireDays") int expireDays,
                                                            @Param("warehouseId") Long warehouseId);

    List<InventoryAlertVO.FrozenAlert> selectFrozenAlerts(@Param("warehouseId") Long warehouseId);

    List<InventoryAlertVO.CapacityAlert> selectOverCapacityAlerts(@Param("warehouseId") Long warehouseId);

    /* ==================== 对账（BR-009） ==================== */

    /** 库存总行数，用于对账报告里的「扫描了多少条」 */
    int countInventoryRows();

    /**
     * 扫出「余额和流水对不上」的库存行。
     *
     * 核心思路：流水是唯一真相，余额是缓存。把每个 (库位, SKU, 批次) 的流水 SUM 一遍，
     * 和 inventory 当前值比，不一样的就是账不平 —— 一行 SQL 就能查出来，不用把全表拉进内存算。
     *
     * 只返回不一致的行（对得上的一行都不返回），所以数据量大的时候这个查询也很轻，
     * 结果通常为空，这也是「对账任务可以随时跑」的前提。
     */
    List<ReconcileVO.Mismatch> selectReconcileMismatches();

    /**
     * 把余额改写成按流水重算出来的值（对账修正专用）。
     *
     * 【这是全项目唯一一处绕过 applyChange 直接写 inventory 的地方，必须解释清楚】
     * applyChange 的语义是「发生了一次库存变动」：它会写一条流水，用来累加出新的余额。
     * 而对账修正不是变动 —— 没有任何货进出仓库，它只是把「派生出来的余额」
     * 重新算了一遍（类似重建缓存 / 索引）。如果硬要走 applyChange，
     * 那个修正流水会改变流水 SUM，导致下一次对账又对不平，永远收敛不了。
     *
     * 所以这里直接改余额，并且带 version 做 CAS：
     * 扫描的时候读到的 version 如果已经变了（说明这一行在扫描之后被正常业务改过），
     * 影响行数就是 0，这次修正作废 —— 下一次对账再说，绝不覆盖别人的正常改动。
     *
     * @return 影响行数。0 表示 version 变了或行已被删除
     */
    int repairBalance(@Param("id") Long id,
                      @Param("qty") int qty,
                      @Param("lockedQty") int lockedQty,
                      @Param("frozenQty") int frozenQty,
                      @Param("version") int version);

    /** 建盘点单时快照一个库位下所有库存行。 */
    List<StocktakingCandidateVO> selectStocktakingCandidates(@Param("locationId") Long locationId);

}
