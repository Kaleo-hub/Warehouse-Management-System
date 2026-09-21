package com.myproject.wmsserver.common.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志出参。
 *
 * 【为什么 detail 要单独给出去查】列表里塞一大段 JSON 没人看，还拖慢查询。
 * 所以列表只给概要（谁、什么时候、做了什么、成没成），
 * 想看改了什么参数再按需查详情。
 */
@Data
public class AuditLogVO {

    private Long id;

    private String traceId;

    private Long userId;

    private String userName;

    private String module;

    private String action;

    private String targetType;

    private String targetId;

    /** 1 成功 0 失败 */
    private Integer result;

    private String errorMsg;

    private Long costMs;

    private String ip;

    private LocalDateTime createTime;

    /** 请求参数 JSON（密码已脱敏） */
    private String detail;
}