package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.inventory.entity.StocktakingOrderItem;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StocktakingOrderItemMapper extends BaseMapper<StocktakingOrderItem> {

    List<StocktakingOrderItemVO> selectByOrderId(@Param("orderId") Long orderId);
}