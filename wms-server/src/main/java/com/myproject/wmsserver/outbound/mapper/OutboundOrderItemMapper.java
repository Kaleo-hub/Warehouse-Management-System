package com.myproject.wmsserver.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.outbound.entity.OutboundOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OutboundOrderItemMapper extends BaseMapper<OutboundOrderItem> {

    List<OutboundOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /** 分配完成后回写「已分配数量」 */
    int updateAllocatedQty(@Param("id") Long id, @Param("allocatedQty") int allocatedQty);

    /**
     * 拣货完成时累加已拣数量。
     * 条件带 picked_qty + qty &lt;= allocated_qty —— 拣货数不可能超过分配数。
     */
    int increasePickedQty(@Param("id") Long id, @Param("qty") int qty);

    /** 释放分配时把已分配数量清零 */
    int resetAllocatedQty(@Param("orderId") Long orderId);
}