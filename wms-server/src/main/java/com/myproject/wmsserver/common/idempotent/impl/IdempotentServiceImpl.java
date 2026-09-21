package com.myproject.wmsserver.common.idempotent.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.myproject.wmsserver.common.idempotent.AcquireResult;
import com.myproject.wmsserver.common.idempotent.IdempotentService;
import com.myproject.wmsserver.common.idempotent.entity.IdempotentRecord;
import com.myproject.wmsserver.common.idempotent.entity.IdempotentStatus;
import com.myproject.wmsserver.common.idempotent.mapper.IdempotentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentServiceImpl implements IdempotentService {

    /** 和建表语句里的长度对齐：idempotent_key varchar(128)、error_msg varchar(255) */
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_ERROR_LENGTH = 255;

    private final IdempotentRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 处理中租约：抢位时预约「我最多占用这么久」。
     *
     * 【为什么是 30 秒】本项目的写接口都是毫秒级（最慢的对账也就几十毫秒），
     * 30 秒是三个数量级的余量 —— 只要进程没死，租约绝不可能在业务跑完之前到期。
     *
     * 【千万别改小】租约一旦在业务还在跑的时候到期，后来的重试就会「接手」，
     * 同一件事被两个线程同时执行 —— 幂等反而被租约破坏了。
     * 真出问题宁可调大，也不要调小。
     */
    @Value("${wms.idempotent.lease-seconds:30}")
    private int leaseSeconds;

    /**
     * 抢位。
     *
     * 【为什么这个方法不加 @Transactional】抢位记录必须【立刻提交】才有意义：
     * 并进外层事务的话，业务还没提交时别的线程看不到这条记录，去重就推迟到了
     * 「业务提交之后」—— 而重复请求恰恰是同一瞬间发生的。
     * 这里靠「单条语句自动提交」实现：INSERT 一执行完别人就能看见。
     *
     * 顺带解决了另一个麻烦：自动提交模式下唯一键冲突只影响这一条语句，
     * 不会把事务标记成 rollback-only，捕获它之后还能继续查记录。
     */
    @Override
    public AcquireResult tryAcquire(String key, String bizType, String requestHash, int ttlMinutes) {
        String safeKey = truncate(key, MAX_KEY_LENGTH);
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(Math.max(1, ttlMinutes));

        LocalDateTime lockedUntil = LocalDateTime.now().plusSeconds(Math.max(1, leaseSeconds));

        int inserted;
        try {
            inserted = recordMapper.tryInsert(safeKey, bizType, requestHash, expireTime, lockedUntil);
        } catch (DuplicateKeyException e) {
            // 并发抢位：另一个线程在 NOT EXISTS 和 INSERT 之间插进去了。
            // 唯一索引兜住了，这里当成「已存在」继续往下判断状态。
            inserted = 0;
        }
        if (inserted > 0) {
            // 我是第一个
            return AcquireResult.acquired();
        }

        // 已经有人来过了：看那行记录处在什么状态
        IdempotentRecord existing = recordMapper.selectOne(Wrappers.<IdempotentRecord>lambdaQuery()
                .eq(IdempotentRecord::getIdempotentKey, safeKey));
        if (existing == null) {
            /*
             * 极小概率：刚判重完，记录就被过期清理删掉了。
             * 这时候当成首次处理（业务侧还有状态机兜底），但要留个日志 ——
             * 如果这种情况频繁出现，说明 expire_time 设得太短了。
             */
            log.warn("幂等记录读取不到（可能刚被清理），按首次处理 key={}", safeKey);
            return AcquireResult.acquired();
        }

        int status = existing.getStatus() == null ? IdempotentStatus.PROCESSING : existing.getStatus();
        if (status == IdempotentStatus.SUCCESS) {
            log.info("幂等命中：直接重放上次结果 key={}", safeKey);
            return AcquireResult.replay(existing.getResultSnapshot());
        }
        if (status == IdempotentStatus.FAILED) {
            int reset = recordMapper.resetToProcessing(safeKey, lockedUntil);
            if (reset > 0) {
                log.info("幂等命中：上次失败，本次放行重试 key={} 上次原因={}",
                        safeKey, existing.getErrorMsg());
                return AcquireResult.retry();
            }
            // 没改成功说明状态又变了（别的线程抢先重试了）→ 按处理中拒绝
        }

        /*
         * 到这里说明记录是「处理中」。但「处理中」不等于「真的还在处理」——
         * 抢位记录是独立提交的，进程要是死在「抢完位、还没销登记」的窗口里，
         * 这行就会永远停在处理中，后来者全被 10007 挡住（用户其实什么都没做成）。
         *
         * 所以看租约：
         *   租约没过期 → 人家确实在跑，拒绝
         *   租约已过期 → 判定持锁进程已经不在了，用 CAS 接手（谁抢到谁执行）
         */
        boolean leaseExpired = existing.getLockedUntil() == null
                || existing.getLockedUntil().isBefore(LocalDateTime.now());
        if (!leaseExpired) {
            log.info("幂等命中：上一次还在处理中（租约未到期 {}）key={}",
                    existing.getLockedUntil(), safeKey);
            return AcquireResult.processing();
        }

        int preempted = recordMapper.preempt(safeKey, requestHash, lockedUntil);
        if (preempted > 0) {
            log.warn("幂等命中：上一次的处理中租约已过期（{}），本次接手重试 key={}",
                    existing.getLockedUntil(), safeKey);
            return AcquireResult.retry();
        }

        // 没抢到说明另一个重试刚刚接手了 —— 还是处理中，继续拒绝
        log.info("幂等命中：租约已过期但已被其他请求接手 key={}", safeKey);
        return AcquireResult.processing();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSuccess(String key, Object result) {
        String snapshot = toJson(result);
        int affected = recordMapper.markSuccess(truncate(key, MAX_KEY_LENGTH), snapshot);
        if (affected == 0) {
            // 记录被清理了，或者状态已经不是处理中 —— 两种都不影响业务结果
            log.warn("幂等记录状态未更新（可能已过期清理）key={}", key);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String key, String errorMsg) {
        int affected = recordMapper.markFailed(
                truncate(key, MAX_KEY_LENGTH), truncate(errorMsg, MAX_ERROR_LENGTH));
        if (affected == 0) {
            log.warn("幂等失败状态未记录 key={} 原因={}", key, errorMsg);
        }
    }

    /**
     * 序列化响应快照（Jackson 3：tools.jackson 包，异常是非受检的）。
     *
     * 序列化失败【不能影响业务】—— 业务已经做完了，
     * 这里只是「顺便存一份供重放」。存不下就下次重试时正常再执行一遍，
     * 反正业务本身还有状态机兜底（比如单据状态不允许重复收货）。
     */
    private String toJson(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException e) {
            // 注意：Jackson 3 的异常是【非受检】的（JacksonException extends RuntimeException），
            // 和 2.x 时代那个到处 throws JsonProcessingException 的写法不一样。
            log.warn("幂等结果快照序列化失败，本次不保存快照", e);
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}