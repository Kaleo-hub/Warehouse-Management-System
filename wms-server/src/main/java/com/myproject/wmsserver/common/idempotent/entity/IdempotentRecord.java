package com.myproject.wmsserver.common.idempotent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录。表结构见 V1.0.0（error_msg 见 V1.0.2）。
 *
 * 一行记录 = 「这一次请求」的身份证：
 *   抢到插进去的那一次负责执行，后来的人只看这行记录决定怎么回应。
 */
@Data
@TableName("idempotent_record")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键，唯一索引就建在这上面 —— 它才是真正的那把锁 */
    private String idempotentKey;

    private String bizType;

    /** 请求体哈希。同一个业务键但参数不同时，用它把两次请求区分开 */
    private String requestHash;

    /** 0 处理中 / 1 成功 / 2 失败 */
    private Integer status;

    /** 成功响应的 JSON 快照，重复请求直接拿它重放 */
    private String resultSnapshot;

    /** 失败原因（status = 2 时有值） */
    private String errorMsg;

    /** 过期时间，给定时清理用 */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}