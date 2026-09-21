package com.myproject.wmsserver.common.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（表 audit_log，见 V1.0.0 建表脚本）。
 *
 * 【和库存流水的分工】两者都叫"日志"，但记的不是一回事：
 *   库存流水：库存数量发生了什么变化（业务数据，只增不改）
 *   审计日志：谁在什么时候调用了哪个写接口、参数是什么、成功还是失败
 * 所以「改密码」会进审计日志（它不改库存），而「上架入库」两边都会留痕。
 */
@Data
@TableName("audit_log")
public class AuditLogRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 和请求日志里的 traceId 一致，方便两边对起来看 */
    private String traceId;

    private Long userId;

    private String userName;

    /** 模块：取 URL 的第一段，如 inbound / inventory / system */
    private String module;

    /** 动作：HTTP 方法 + 方法名，如 "POST receive" */
    private String action;

    /** 操作对象类型：控制器名去掉 Controller，如 InboundOrder */
    private String targetType;

    /** 操作对象 id：参数里第一个数字类型的值（通常是路径上的 {id}） */
    private String targetId;

    /** 请求参数 JSON（密码类字段已脱敏） */
    private String detail;

    private String ip;

    /** 1 成功 0 失败 */
    private Integer result;

    private String errorMsg;

    /** 耗时(毫秒) */
    private Long costMs;

    private LocalDateTime createTime;
}