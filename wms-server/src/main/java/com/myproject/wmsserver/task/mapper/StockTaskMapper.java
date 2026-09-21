package com.myproject.wmsserver.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.entity.StockTask;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface StockTaskMapper extends BaseMapper<StockTask> {

    /** 分页查询，带仓库名 / 库位编码 / 商品名 */
    IPage<StockTaskVO> selectTaskPage(IPage<StockTaskVO> page,
                                      @Param("query") StockTaskQuery query);

    StockTaskVO selectTaskDetail(@Param("id") Long id);

    /**
     * 领取任务：CREATED → ASSIGNED，用 CAS。
     * 不能先查再改 —— 两个作业员同时点「领取」，先查再改会两个都通过。
     *
     * @return 影响行数。0 表示已被别人领走
     */
    int assign(@Param("id") Long id,
               @Param("assigneeId") Long assigneeId,
               @Param("assigneeName") String assigneeName);

    /** 开始作业：ASSIGNED → PROCESSING */
    int start(@Param("id") Long id);

    /** 完成作业：PROCESSING → DONE */
    int complete(@Param("id") Long id,
                 @Param("actualQty") int actualQty,
                 @Param("remark") String remark);

    /**
     * 库位推荐（上架策略）。
     *
     * 【为什么这个跨模块的查询放在任务模块的 Mapper 里】
     * docs/02 规定「跨模块只能调 Service，不能注入别人的 Mapper」——
     * 这里没有注入 LocationMapper / InventoryMapper，而是任务模块自己发一条只读 JOIN，
     * 属于「读投影」而不是「写别人的表」，规则没有被破坏。
     * 如果以后推荐策略变复杂（要考虑 ABC 分类、波次、承重），
     * 再抽成独立的 PutawayStrategy 组件。
     */
    Long selectRecommendedPutawayLocationId(@Param("warehouseId") Long warehouseId,
                                            @Param("skuId") Long skuId);

    /**
     * ① 超时打标：过了 deadline 还没做完的任务 → TIMEOUT。
     *
     * 为什么先标记再回池，而不是一条 SQL 直接改回 CREATED：
     * 这是 docs/01 第 6.3 节画的状态机 —— 「超时」和「重新入池」是两个决策，
     * 中间那个 TIMEOUT 状态是有意义的：它代表「确实超时过」，
     * 重试次数用完之后就停在这个状态等人处理，不会再被扫回来。
     */
    int markTimeout(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * ② 重新入池：TIMEOUT 且还没重试过的 → 回到 CREATED，重试次数 +1。
     * 只给一次机会（retry_count < 1）：反复重试等于把问题藏起来，该让人看见了。
     */
    int requeueTimeout(@Param("newDeadline") LocalDateTime newDeadline, @Param("limit") int limit);

    /** 取该仓库的收货暂存位（location_type = 'TEMP'），作为上架任务的源库位 */
    Long selectTempLocationId(@Param("warehouseId") Long warehouseId);

    /**
     * 作废某张来源单据下所有未完成的拣货任务（BR-010 超时释放用）。
     *
     * 一条 UPDATE 搞定，不先把任务查出来再逐条改：
     * 逐条改既慢、又有「查到一半状态变了」的窗口，而 update ... where status in (...) 天然是原子的。
     * 已经 DONE / CANCELLED 的任务不动 —— 完成过的活不能因为超时被抹掉。
     */
    int cancelUnfinishedTasks(@Param("sourceType") String sourceType,
                              @Param("sourceId") Long sourceId,
                              @Param("taskType") String taskType,
                              @Param("reason") String reason);
}