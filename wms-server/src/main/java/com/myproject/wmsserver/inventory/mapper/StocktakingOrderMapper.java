package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderQuery;
import com.myproject.wmsserver.inventory.entity.StocktakingOrder;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderVO;
import org.apache.ibatis.annotations.Param;

public interface StocktakingOrderMapper extends BaseMapper<StocktakingOrder> {

    IPage<StocktakingOrderVO> selectOrderPage(IPage<StocktakingOrderVO> page,
                                              @Param("query") StocktakingOrderQuery query);

    StocktakingOrderVO selectOrderDetail(@Param("id") Long id);

    StocktakingOrder selectForUpdate(@Param("id") Long id);

    int updateCountSummary(@Param("id") Long id,
                           @Param("countedItems") int countedItems,
                           @Param("diffItems") int diffItems,
                           @Param("gainQty") int gainQty,
                           @Param("lossQty") int lossQty,
                           @Param("updateBy") Long updateBy);

    int markCompleted(@Param("id") Long id,
                      @Param("countedItems") int countedItems,
                      @Param("diffItems") int diffItems,
                      @Param("gainQty") int gainQty,
                      @Param("lossQty") int lossQty,
                      @Param("updateBy") Long updateBy);

    int cancel(@Param("id") Long id, @Param("updateBy") Long updateBy);
}