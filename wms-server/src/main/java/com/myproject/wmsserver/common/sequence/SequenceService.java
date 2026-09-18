package com.myproject.wmsserver.common.sequence;

/** 单号发号器，入库单 / 出库单 / 任务号共用 */
public interface SequenceService {

    /**
     * 生成单号：前缀 + yyyyMMdd + 6 位序号，例如 IN20260918000001。
     * 每天从 1 重新开始。
     *
     * @param bizType 业务类型，决定序号跟谁一起自增，取值见 sys_sequence 表注释
     * @param prefix  单号前缀，例如 "IN"
     */
    String nextNo(String bizType, String prefix);
}