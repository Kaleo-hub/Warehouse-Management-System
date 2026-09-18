package com.myproject.wmsserver.common.exception;

import lombok.Getter;

/** 入库模块错误码（4xxxx 段，见 docs/02 第 4.3 节） */
@Getter
public enum InboundErrorCode implements ErrorCode {

    /** 状态机不允许该操作，例如对「已收货」的单据再收货、取消非 CREATED 的单据 */
    STATUS_NOT_ALLOWED(40002, "入库单状态不允许该操作"),

    /** 累计实收不能超过预计数量 */
    RECEIVED_EXCEED_EXPECT(40004, "实收数量超过预计数量"),

    /** 收货明细里出现了不属于该单据的 itemId */
    ITEM_NOT_IN_ORDER(40005, "收货明细不属于该入库单"),

    /** 建单时引用了不存在或已停用的商品 */
    SKU_NOT_AVAILABLE(40007, "商品不存在或已停用，不能开单");

    private final int code;
    private final String message;

    InboundErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}