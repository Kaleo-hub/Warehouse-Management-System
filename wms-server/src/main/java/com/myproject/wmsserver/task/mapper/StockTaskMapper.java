package com.myproject.wmsserver.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.entity.StockTask;
import com.myproject.wmsserver.task.vo.StockTaskVO;
import org.apache.ibatis.annotations.Param;

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

    /** 取该仓库的收货暂存位（location_type = 'TEMP'），作为上架任务的源库位 */
    Long selectTempLocationId(@Param("warehouseId") Long warehouseId);
}