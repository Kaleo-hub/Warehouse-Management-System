package com.myproject.wmsserver.common.idempotent;

/**
 * 抢位结果。
 *
 * 四个分支对应四种处理方式，见 IdempotentService#tryAcquire 的说明。
 */
public record AcquireResult(AcquireStatus status, String snapshot) {

    public enum AcquireStatus {
        /** 抢到了：这是第一次，去执行业务 */
        ACQUIRED,
        /** 之前成功过：直接把上次的响应快照还回去，业务不执行 */
        REPLAY,
        /** 之前那次还在跑：拒绝，让调用方稍后再试 */
        PROCESSING,
        /** 之前那次失败了：放行重试 */
        RETRY
    }

    public static AcquireResult acquired() {
        return new AcquireResult(AcquireStatus.ACQUIRED, null);
    }

    public static AcquireResult replay(String snapshot) {
        return new AcquireResult(AcquireStatus.REPLAY, snapshot);
    }

    public static AcquireResult processing() {
        return new AcquireResult(AcquireStatus.PROCESSING, null);
    }

    public static AcquireResult retry() {
        return new AcquireResult(AcquireStatus.RETRY, null);
    }
}