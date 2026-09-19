package com.myproject.wmsserver.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.entity.OutboundOrder;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import org.apache.ibatis.annotations.Param;

public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {

    IPage<OutboundOrderVO> selectOrderPage(IPage<OutboundOrderVO> page,
                                           @Param("query") OutboundOrderQuery query);

    OutboundOrderVO selectOrderDetail(@Param("id") Long id);

    /** 分配前锁单据行：要读明细、算总量、再写回，中间不能被插队 */
    OutboundOrder selectForUpdate(@Param("id") Long id);

    /**
     * 状态流转统一用 CAS（带 status 条件）。
     * 影响行数为 0 说明状态已经被别人改过 —— 直接抛业务异常，不要静默放过。
     */
    int markAllocated(@Param("id") Long id,
                      @Param("allocatedQty") int allocatedQty,
                      @Param("updateBy") Long updateBy);

    int markPicking(@Param("id") Long id, @Param("updateBy") Long updateBy);

    int markPicked(@Param("id") Long id,
                   @Param("pickedQty") int pickedQty,
                   @Param("updateBy") Long updateBy);

    int markShipped(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /** 释放分配：ALLOCATED → CREATED */
    int markReleased(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /** 取消：只有 CREATED 可以 */
    int cancel(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /**
     * 该出库单下还有没有未完成的拣货任务。
     * 释放分配前必须检查 —— 否则已经把锁定还回去了，作业员还拿着老任务去拣货。
     *
     * 【为什么读 stock_task】docs/02 规定跨模块只能调 Service。
     * 这里没有注入 task 模块的 Mapper，而是本模块自己发一条只读 COUNT，
     * 属于「读投影」而不是「写别人的表」，规则没有被破坏。
     */
    int countUnfinishedPickTask(@Param("orderId") Long orderId);
}