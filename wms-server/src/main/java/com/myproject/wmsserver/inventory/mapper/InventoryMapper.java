package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import org.apache.ibatis.annotations.Param;
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
                  @Param("batchNo") String batchNo);

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
}