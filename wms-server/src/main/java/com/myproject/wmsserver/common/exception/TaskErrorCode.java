package com.myproject.wmsserver.common.exception;

import lombok.Getter;

/** 作业任务错误码（7xxxx 段） */
@Getter
public enum TaskErrorCode implements ErrorCode {

    /** 并发领取：别人已经先领走了 */
    TASK_ALREADY_ASSIGNED(70003, "任务已被领取"),

    /** 状态机不允许该动作 */
    TASK_STATUS_NOT_ALLOWED(70005, "任务状态不允许该操作"),

    /** 上架任务没推荐到目标库位 */
    TASK_TARGET_LOCATION_MISSING(70006, "任务没有目标库位，无法上架"),

    /** 任务来源单据不存在或状态不对 */
    TASK_SOURCE_INVALID(70007, "任务来源单据不合法"),

    /** 拣货必须按分配数量完成；短拣会把未实际拣出的货也核销掉 */
    PICK_QTY_MISMATCH(70008, "拣货数量必须等于分配数量"),

    /** 当前只支持上架和拣货任务，其他任务类型不能误走上架逻辑 */
    TASK_TYPE_NOT_SUPPORTED(70009, "任务类型不支持该操作");

    private final int code;
    private final String message;

    TaskErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
