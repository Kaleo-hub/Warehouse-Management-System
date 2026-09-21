package com.myproject.wmsserver.common.idempotent;

/**
 * 幂等记录服务。
 *
 * 【分层原因】切面只负责「从方法上把 key 和参数抠出来」，
 * 真正的状态机（抢位 / 重放 / 处理中 / 允许重试）都在这里，
 * 这样业务代码和切面都不会被状态分支污染。
 */
public interface IdempotentService {

    /**
     * 抢位：尝试把这个 key 下的「第一次执行权」拿到手。
     *
     * 实现要点：靠 idempotent_record 的唯一索引抢，不是先查再插 ——
     * 先查再插在并发下两个请求都会查不到，然后都插进去，去重直接失效。
     */
    AcquireResult tryAcquire(String key, String bizType, String requestHash, int ttlMinutes);

    /** 业务执行成功：记录状态和响应快照 */
    void markSuccess(String key, Object result);

    /** 业务执行失败：记下原因，允许下次重试 */
    void markFailed(String key, String errorMsg);
}