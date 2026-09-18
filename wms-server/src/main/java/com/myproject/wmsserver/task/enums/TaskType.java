package com.myproject.wmsserver.task.enums;

/** 作业任务类型，对应 stock_task.task_type */
public enum TaskType {

    /** 上架：收货暂存位 → 存储位，完成后库存增加 */
    PUTAWAY,

    /** 拣货：从库位取出，出库流程用 */
    PICK,

    /** 移库 */
    TRANSFER,

    /** 盘点 */
    COUNT
}