package com.myproject.wmsserver.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.outbound.dto.OutboundOrderQuery;
import com.myproject.wmsserver.outbound.entity.OutboundOrder;
import com.myproject.wmsserver.outbound.vo.OutboundOrderVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
     * 复制原单表头，生成红冲单。用 INSERT ... SELECT 直接在库里复制，
     * 不把原单读进内存再拼一遍（那样字段一多就容易漏）。
     *
     * 红冲单状态直接给 SHIPPED：货已经回来了，这张单在业务上已经完成，
     * 它存在的意义就是记账和留痕。
     */
    int copyAsReverseOrder(@Param("newOrderNo") String newOrderNo,
                           @Param("originalId") Long originalId,
                           @Param("createBy") Long createBy);

    /** 复制原单明细到红冲单 */
    int copyItemsAsReverse(@Param("newOrderId") Long newOrderId,
                           @Param("originalId") Long originalId);

    /**
     * 把原单标记为「已被红冲」。CAS 带 status='SHIPPED'，
     * 并发时只有一个能成功；再配上 uk_reverse_of 唯一索引，双保险。
     */
    int markReversed(@Param("id") Long id,
                     @Param("reversedById") Long reversedById,
                     @Param("updateBy") Long updateBy);

    /**
     * 该出库单下还有没有未完成的拣货任务。
     * 释放分配前必须检查 —— 否则已经把锁定还回去了，作业员还拿着老任务去拣货。
     *
     * 【为什么读 stock_task】docs/02 规定跨模块只能调 Service。
     * 这里没有注入 task 模块的 Mapper，而是本模块自己发一条只读 COUNT，
     * 属于「读投影」而不是「写别人的表」，规则没有被破坏。
     */
    int countUnfinishedPickTask(@Param("orderId") Long orderId);

    /**
     * 找出「已经分配、但分配时间早于 deadline 还没往下走」的单据 id（BR-010）。
     *
     * 只找 ALLOCATED：
     *   - 还没生成拣货任务 → 锁定白占着
     *   - 不能找 PICKING：那时候货可能已经被从货架上拿下来了，
     *     把锁定还回去等于账实不符，只能往前走（见 OutboundStatus.canRelease 的注释）
     *
     * @param limit 单次最多取多少条，避免一次定时任务锁太多行
     */
    List<Long> selectTimeoutAllocatedIds(@Param("deadline") LocalDateTime deadline,
                                         @Param("limit") int limit);
}