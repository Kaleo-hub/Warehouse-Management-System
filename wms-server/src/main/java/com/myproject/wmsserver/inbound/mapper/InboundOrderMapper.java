package com.myproject.wmsserver.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inbound.dto.InboundOrderQuery;
import com.myproject.wmsserver.inbound.entity.InboundOrder;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;
import org.apache.ibatis.annotations.Param;

public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    /**
     * 分页查询（带仓库名）。
     * 分页插件要求 IPage 参数放在第一位，返回值也必须是 IPage。
     */
    IPage<InboundOrderVO> selectOrderPage(IPage<InboundOrderVO> page,
                                          @Param("query") InboundOrderQuery query);

    /** 详情（带仓库名）。明细由调用方另外查，避免一条 SQL 里做集合映射 */
    InboundOrderVO selectOrderDetail(@Param("id") Long id);

    /**
     * 按 id 查询并加排他锁，收货时用。
     * 作用：同一张单据不允许被两个请求同时收货，否则 total_received_qty 会算错。
     */
    InboundOrder selectForUpdate(@Param("id") Long id);

    /** 收货完成后回写状态和实收总量 */
    int updateReceiveResult(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("totalReceivedQty") int totalReceivedQty,
                            @Param("updateBy") Long updateBy);

    /**
     * 取消单据。
     * 不用先查后改，一条带 status 条件的 UPDATE 就够了：
     * 影响行数 0 就说明状态不对（已经被别人取消/收货了）。
     */
    int cancel(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /**
     * 所有明细都上架完成后，把单据推到 PUTAWAY_DONE。
     * 带 status = 'RECEIVED' 条件做 CAS —— 并发完成任务时只有第一个能推进。
     */
    int markPutawayDone(@Param("id") Long id, @Param("updateBy") Long updateBy);
}