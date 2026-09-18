package com.myproject.wmsserver.common.sequence.impl;

import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.common.sequence.mapper.SysSequenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 发号器实现。
 *
 * 【为什么用 REQUIRES_NEW】
 * 序号一旦发出去就不应该因为业务回滚而回收 —— 否则并发下会出现
 * "A 发了 0006 但业务失败回滚，B 又发了 0006" 的重号。
 * 用 REQUIRES_NEW 让发号走独立事务：业务失败不影响序号已消耗的事实，
 * 代价只是单号可能有空洞（0006 作废、下一单是 0007）—— 这完全能接受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SequenceServiceImpl implements SequenceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String NO_FORMAT = "%06d";

    private final SysSequenceMapper sequenceMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextNo(String bizType, String prefix) {
        String bizDate = LocalDate.now().format(DATE_FORMAT);

        sequenceMapper.nextValue(bizType, bizDate);

        Long current = sequenceMapper.selectCurrentValue(bizType, bizDate);
        if (current == null) {
            log.error("发号失败 bizType={} bizDate={}", bizType, bizDate);
            throw new BizException(CommonErrorCode.SEQUENCE_GENERATE_FAILED);
        }

        return prefix + bizDate + String.format(NO_FORMAT, current);
    }
}