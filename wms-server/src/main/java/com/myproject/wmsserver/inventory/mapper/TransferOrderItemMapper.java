package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.inventory.entity.TransferOrderItem;
import com.myproject.wmsserver.inventory.vo.TransferOrderItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TransferOrderItemMapper extends BaseMapper<TransferOrderItem> {

    List<TransferOrderItemVO> selectByOrderId(@Param("orderId") Long orderId);
}