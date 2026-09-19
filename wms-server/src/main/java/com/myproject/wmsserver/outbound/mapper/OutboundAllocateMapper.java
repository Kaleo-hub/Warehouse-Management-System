package com.myproject.wmsserver.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.outbound.entity.OutboundAllocate;
import com.myproject.wmsserver.outbound.vo.OutboundAllocateVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OutboundAllocateMapper extends BaseMapper<OutboundAllocate> {

    /** 详情页展示用，带库位编码 / 商品编码名称 */
    List<OutboundAllocateVO> selectVOByOrderId(@Param("orderId") Long orderId);

    /** 按状态取分配记录：释放分配取 LOCKED、生成拣货任务也取 LOCKED */
    List<OutboundAllocate> selectByOrderIdAndStatus(@Param("orderId") Long orderId,
                                                    @Param("status") String status);

    /** 拣货完成：LOCKED → PICKED，带 CAS。影响行数 0 说明已经被别人拣过了 */
    int markPicked(@Param("id") Long id, @Param("pickedQty") int pickedQty);

    /** 释放分配：LOCKED → RELEASED，带 CAS */
    int markReleased(@Param("id") Long id);
}