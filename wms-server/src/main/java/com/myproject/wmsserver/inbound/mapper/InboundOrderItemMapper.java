package com.myproject.wmsserver.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.inbound.entity.InboundOrderItem;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface InboundOrderItemMapper extends BaseMapper<InboundOrderItem> {

    /** 按单据 id 查全部明细，按 id 升序保证顺序稳定 */
    List<InboundOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 收货：实收数量是【累加】不是覆盖，所以写 received_qty = received_qty + #{receivedQty}。
     * 批号和日期允许覆盖（供应商分批送货时可能补登）。
     */
    int receiveItem(@Param("id") Long id,
                    @Param("receivedQty") int receivedQty,
                    @Param("batchNo") String batchNo,
                    @Param("productionDate") LocalDate productionDate,
                    @Param("expiryDate") LocalDate expiryDate);

    /**
     * 上架完成时累加已上架数量。
     * 条件里带 putaway_qty + qty &lt;= received_qty —— 上架数不可能超过实收数，
     * 万一被绕过校验，数据库这一层也会拦住（影响行数 0）。
     */
    int increasePutawayQty(@Param("id") Long id, @Param("qty") int qty);
}