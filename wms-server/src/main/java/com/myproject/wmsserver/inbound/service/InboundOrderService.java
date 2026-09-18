package com.myproject.wmsserver.inbound.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inbound.dto.InboundOrderQuery;
import com.myproject.wmsserver.inbound.dto.InboundOrderSaveDTO;
import com.myproject.wmsserver.inbound.dto.InboundReceiveDTO;
import com.myproject.wmsserver.inbound.vo.InboundOrderVO;

public interface InboundOrderService {

    /** 分页查询 */
    PageResult<InboundOrderVO> page(InboundOrderQuery query);

    /** 详情（含明细） */
    InboundOrderVO get(Long id);

    /** 创建入库单，返回新 id */
    Long create(InboundOrderSaveDTO dto);

    /**
     * 收货登记。
     *
     * 【最重要的一条约束】这一步【不碰库存表】。
     * 收货只是登记"收到了多少"，货还在收货暂存区；
     * 库存要等上架完成才增加（docs/01 第 5.1 节）。
     */
    void receive(Long orderId, InboundReceiveDTO dto);

    /** 取消入库单，只有 CREATED 允许 */
    void cancel(Long orderId);

    /**
     * 上架完成回写（由 StockTaskService 调用）。
     *
     * 做两件事：
     *   1. 累加明细的 putaway_qty
     *   2. 如果整单所有明细都上架完了，把单据 CAS 推到 PUTAWAY_DONE
     *
     * 为什么不让任务模块直接改 inbound_order_item？
     * 单据的状态流转必须由入库模块自己负责，别的模块只能"通知"它发生了什么。
     */
    void onPutawayCompleted(Long orderId, Long orderItemId, int qty);
}