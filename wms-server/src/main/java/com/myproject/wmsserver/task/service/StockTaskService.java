package com.myproject.wmsserver.task.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.task.dto.StockTaskCompleteDTO;
import com.myproject.wmsserver.task.dto.StockTaskQuery;
import com.myproject.wmsserver.task.vo.StockTaskVO;

public interface StockTaskService {

    PageResult<StockTaskVO> page(StockTaskQuery query);

    StockTaskVO get(Long id);

    /**
     * 按入库单的已收货明细生成上架任务。
     * 幂等：同一明细已有未完成任务时不重复生成，返回 10007。
     *
     * @return 本次生成的任务数
     */
    int generatePutawayTasks(Long inboundOrderId);

    /** 领取任务：CREATED → ASSIGNED。被人抢先返回 70003 */
    void assign(Long taskId);

    /** 开始作业：ASSIGNED → PROCESSING */
    void start(Long taskId);

    /**
     * 完成上架：PROCESSING → DONE。
     *
     * 【整个入库链路的收口】
     * 这是全流程唯一一处真正增加库存的地方 ——
     * 收货不增加、上架完成才增加（docs/01 第 5.1 节）。
     *
     * 一次调用要做完：
     *   1. 任务状态 CAS（先占坑，防并发重复执行）
     *   2. 调 InventoryService.applyChange 加库存 + 写流水
     *   3. 增加库位已用容量（BR-006）
     *   4. 回写入库单明细 putaway_qty，全部上架完把单据推到 PUTAWAY_DONE
     */
    void complete(Long taskId, StockTaskCompleteDTO dto);
}